package com.washcare.crm.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.UserAccount;
import com.washcare.crm.auth.UserAccountRepository;
import com.washcare.crm.booking.Booking;
import com.washcare.crm.notification.NotificationService;
import com.washcare.crm.retention.LoyaltyService;

@Service
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserAccountRepository userRepository;
    private final LoyaltyService loyaltyService;
    private final NotificationService notificationService;

    public BillingService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            UserAccountRepository userRepository,
            LoyaltyService loyaltyService,
            NotificationService notificationService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.loyaltyService = loyaltyService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Invoice issueForCompletedBooking(Booking booking) {
        return invoiceRepository.findByBookingId(booking.getId())
                .orElseGet(() -> invoiceRepository.save(new Invoice(
                        booking,
                        "WC-" + LocalDate.now() + "-" + booking.getId())));
    }

    @Transactional
    public Invoice recordPayment(
            Long staffUserId,
            Long invoiceId,
            BigDecimal amount,
            PaymentMethod method,
            String idempotencyKey) {
        Invoice invoice = invoiceRepository.lockById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));

        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            if (!payment.getInvoice().getId().equals(invoiceId)
                    || payment.getAmount().compareTo(amount) != 0
                    || payment.getMethod() != method) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This payment request key was already used for a different payment.");
            }
            return invoice;
        }

        BigDecimal paidAmount = paymentRepository.sumAmountByInvoiceIdAndStatus(invoiceId, PaymentStatus.PAID);
        BigDecimal balance = invoice.getTotalAmount().subtract(paidAmount);
        if (amount.compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment exceeds the outstanding invoice balance.");
        }

        UserAccount actor = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));
        paymentRepository.save(new Payment(invoice, amount, method, actor, idempotencyKey));
        paymentRepository.flush();
        BigDecimal updatedPaidAmount = paymentRepository.sumAmountByInvoiceIdAndStatus(invoiceId, PaymentStatus.PAID);
        loyaltyService.awardForPaidInvoice(invoice, updatedPaidAmount);
        notificationService.paymentRecorded(invoice, invoice.getTotalAmount().compareTo(updatedPaidAmount) == 0);
        return invoice;
    }

    @Transactional(readOnly = true)
    public BigDecimal paidAmount(Invoice invoice) {
        return paymentRepository.sumAmountByInvoiceIdAndStatus(invoice.getId(), PaymentStatus.PAID);
    }
}
