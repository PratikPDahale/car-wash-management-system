package com.washcare.crm.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;
import com.washcare.crm.retention.LoyaltyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final LoyaltyService loyaltyService;

    public InvoiceController(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            BillingService billingService,
            LoyaltyService loyaltyService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
        this.loyaltyService = loyaltyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getShopInvoices() {
        return invoiceRepository.findAllByOrderByIssuedAtDesc().stream().map(this::toResponse).toList();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getMyInvoices(Authentication authentication) {
        return invoiceRepository.findCustomerInvoices(currentUser(authentication).id())
                .stream().map(this::toResponse).toList();
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional
    public InvoiceResponse recordPayment(
            Authentication authentication,
            @PathVariable Long invoiceId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        Invoice invoice = billingService.recordPayment(
                currentUser(authentication).id(),
                invoiceId,
                request.amount(),
                request.method(),
                idempotencyKey.trim());
        return toResponse(invoice);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        BigDecimal paidAmount = billingService.paidAmount(invoice);
        List<PaymentResponse> payments = paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoice.getId())
                .stream()
                .map(payment -> new PaymentResponse(
                        payment.getId(),
                        payment.getAmount(),
                        payment.getMethod().name(),
                        payment.getStatus().name(),
                        payment.getPaidAt(),
                        payment.getRecordedBy().getFullName()))
                .toList();
        var booking = invoice.getBooking();
        return new InvoiceResponse(
                invoice.getId(),
                booking.getId(),
                invoice.getInvoiceNo(),
                booking.getCustomer().getId(),
                booking.getCustomer().getName(),
                booking.getService().getName(),
                booking.getVehicle().getRegistrationNo(),
                booking.getBookingDate(),
                invoice.getSubtotal(),
                invoice.getDiscount(),
                invoice.getTotalAmount(),
                paidAmount,
                invoice.getTotalAmount().subtract(paidAmount),
                loyaltyService.balance(booking.getCustomer().getId()),
                loyaltyService.maxRedeemablePoints(invoice),
                invoice.getIssuedAt(),
                payments);
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record CreatePaymentRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @NotNull PaymentMethod method) {
    }

    public record InvoiceResponse(
            Long id,
            Long bookingId,
            String invoiceNo,
            Long customerId,
            String customerName,
            String serviceName,
            String registrationNo,
            LocalDate date,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal balance,
            int customerLoyaltyPoints,
            int maxRedeemablePoints,
            Instant issuedAt,
            List<PaymentResponse> payments) {
    }

    public record PaymentResponse(
            Long id,
            BigDecimal amount,
            String method,
            String status,
            Instant paidAt,
            String recordedBy) {
    }
}
