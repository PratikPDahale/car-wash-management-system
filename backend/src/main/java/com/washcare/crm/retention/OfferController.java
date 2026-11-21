package com.washcare.crm.retention;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/v1/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'CUSTOMER')")
    @Transactional(readOnly = true)
    public List<OfferResponse> getActiveOffers() {
        return offerService.activeOffers().stream().map(OfferController::toResponse).toList();
    }

    @GetMapping("/manage")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional(readOnly = true)
    public List<OfferResponse> getAllOffers() {
        return offerService.allOffers().stream().map(OfferController::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public OfferResponse create(@Valid @RequestBody OfferRequest request) {
        return toResponse(offerService.create(request.toData()));
    }

    @PutMapping("/{offerId}")
    @PreAuthorize("hasRole('OWNER')")
    public OfferResponse update(@PathVariable Long offerId, @Valid @RequestBody OfferRequest request) {
        return toResponse(offerService.update(offerId, request.toData()));
    }

    private static OfferResponse toResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(), offer.getName(), offer.getDescription(), offer.getDiscountType().name(),
                offer.getDiscountValue(), offer.getStartsAt(), offer.getEndsAt(),
                offer.getEligibility(), offer.isActive());
    }

    public record OfferRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull OfferDiscountType discountType,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            @Size(max = 500) String eligibility,
            boolean active) {
        OfferService.OfferData toData() {
            return new OfferService.OfferData(
                    name, description, discountType, discountValue, startsAt, endsAt, eligibility, active);
        }
    }

    public record OfferResponse(
            Long id, String name, String description, String discountType, BigDecimal discountValue,
            LocalDateTime startsAt, LocalDateTime endsAt, String eligibility, boolean active) { }
}
