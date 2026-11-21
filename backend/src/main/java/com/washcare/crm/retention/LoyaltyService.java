package com.washcare.crm.retention;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.billing.Invoice;
import com.washcare.crm.billing.InvoiceRepository;
import com.washcare.crm.billing.PaymentRepository;
import com.washcare.crm.billing.PaymentStatus;
import com.washcare.crm.booking.Booking;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;
import com.washcare.crm.notification.NotificationService;

@Service
public class LoyaltyService {

    private static final BigDecimal SPEND_PER_POINT = new BigDecimal("100.00");
    private static final BigDecimal DISCOUNT_PER_POINT = new BigDecimal("0.10");
    private static final BigDecimal MAX_REDEMPTION_RATE = new BigDecimal("0.20");

    private final LoyaltyTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    public LoyaltyService(
            LoyaltyTransactionRepository transactionRepository,
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public int balance(Long customerId) {
        return Math.toIntExact(transactionRepository.balanceForCustomer(customerId));
    }

    @Transactional(readOnly = true)
    public int maxRedeemablePoints(Invoice invoice) {
        BigDecimal maxDiscount = invoice.getSubtotal().multiply(MAX_REDEMPTION_RATE).setScale(2, RoundingMode.DOWN);
        return maxDiscount.multiply(BigDecimal.TEN).intValueExact();
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransaction> history(Long customerId) {
        return transactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional
    public void awardForPaidInvoice(Invoice invoice, BigDecimal paidAmount) {
        if (invoice.getTotalAmount().compareTo(paidAmount) != 0
                || transactionRepository.existsByInvoiceIdAndTransactionType(
                        invoice.getId(), LoyaltyTransactionType.EARN)) {
            return;
        }

        int earnedPoints = paidAmount.divide(SPEND_PER_POINT, 0, RoundingMode.DOWN).intValueExact();
        if (earnedPoints <= 0) return;
        Customer customer = invoice.getBooking().getCustomer();
        transactionRepository.save(new LoyaltyTransaction(
                customer,
                invoice.getBooking(),
                invoice,
                earnedPoints,
                LoyaltyTransactionType.EARN,
                "Earned for fully paid invoice " + invoice.getInvoiceNo()));
        notificationService.loyaltyEarned(customer, earnedPoints);
    }

    @Transactional
    public Invoice redeemForInvoice(Long customerId, Long invoiceId, int pointsToRedeem) {
        if (pointsToRedeem <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points to redeem must be greater than zero.");
        }
        Customer customer = customerRepository.lockById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        Invoice invoice = invoiceRepository.lockById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        if (!invoice.getBooking().getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found for this customer.");
        }

        var existingRedemption = transactionRepository.findByInvoiceIdAndTransactionType(
                invoiceId, LoyaltyTransactionType.REDEEM);
        if (existingRedemption.isPresent()) {
            if (existingRedemption.get().getTransactionType() == LoyaltyTransactionType.REDEEM
                    && -existingRedemption.get().getPointsDelta() == pointsToRedeem) {
                return invoice;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loyalty points were already redeemed for this invoice.");
        }

        BigDecimal paid = paymentRepository.sumAmountByInvoiceIdAndStatus(invoiceId, PaymentStatus.PAID);
        if (paid.signum() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Loyalty points must be applied before recording a payment.");
        }

        int availablePoints = balance(customerId);
        if (pointsToRedeem > availablePoints) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The customer does not have enough loyalty points.");
        }
        BigDecimal maxDiscount = invoice.getSubtotal().multiply(MAX_REDEMPTION_RATE).setScale(2, RoundingMode.DOWN);
        int maxPointsForInvoice = maxDiscount.multiply(BigDecimal.TEN).intValueExact();
        if (pointsToRedeem > maxPointsForInvoice) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Redemption is limited to 20% of the invoice subtotal.");
        }
        BigDecimal discount = DISCOUNT_PER_POINT.multiply(BigDecimal.valueOf(pointsToRedeem));
        invoice.applyLoyaltyDiscount(discount);
        transactionRepository.save(new LoyaltyTransaction(
                customer,
                invoice.getBooking(),
                invoice,
                -pointsToRedeem,
                LoyaltyTransactionType.REDEEM,
                "Redeemed for invoice " + invoice.getInvoiceNo()));
        notificationService.loyaltyRedeemed(customer, pointsToRedeem, invoice.getBooking().getId());
        return invoice;
    }

    @Transactional
    public Invoice redeemForInvoice(Long invoiceId, int pointsToRedeem) {
        Long customerId = invoiceRepository.findById(invoiceId)
                .map(invoice -> invoice.getBooking().getCustomer().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        return redeemForInvoice(customerId, invoiceId, pointsToRedeem);
    }
}
