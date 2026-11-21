package com.washcare.crm.retention;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/loyalty")
public class LoyaltyController {

    private final CustomerRepository customerRepository;
    private final LoyaltyService loyaltyService;

    public LoyaltyController(CustomerRepository customerRepository, LoyaltyService loyaltyService) {
        this.customerRepository = customerRepository;
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public LoyaltyResponse getMyPoints(Authentication authentication) {
        Customer customer = customerRepository.findByUserId(currentUser(authentication).id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Customer profile not found."));
        return response(customer);
    }

    @PostMapping("/invoices/{invoiceId}/redeem")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional
    public LoyaltyRedemptionResponse redeem(
            @PathVariable Long invoiceId,
            @Valid @RequestBody RedeemRequest request) {
        var invoice = loyaltyService.redeemForInvoice(invoiceId, request.points());
        return new LoyaltyRedemptionResponse(
                invoice.getId(), invoice.getDiscount(), invoice.getTotalAmount(),
                loyaltyService.balance(invoice.getBooking().getCustomer().getId()));
    }

    private LoyaltyResponse response(Customer customer) {
        List<LoyaltyTransactionResponse> history = loyaltyService.history(customer.getId()).stream()
                .map(item -> new LoyaltyTransactionResponse(
                        item.getId(), item.getPointsDelta(), item.getTransactionType().name(),
                        item.getReason(), item.getCreatedAt()))
                .toList();
        return new LoyaltyResponse(customer.getId(), loyaltyService.balance(customer.getId()), history);
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record RedeemRequest(@Min(1) int points) { }

    public record LoyaltyResponse(Long customerId, int balance, List<LoyaltyTransactionResponse> transactions) { }

    public record LoyaltyTransactionResponse(
            Long id, int pointsDelta, String type, String reason, Instant createdAt) { }

    public record LoyaltyRedemptionResponse(Long invoiceId, java.math.BigDecimal discount,
            java.math.BigDecimal totalAmount, int remainingPoints) { }
}
