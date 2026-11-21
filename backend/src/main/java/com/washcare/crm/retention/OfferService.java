package com.washcare.crm.retention;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.notification.NotificationService;

@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final NotificationService notificationService;

    public OfferService(OfferRepository offerRepository, NotificationService notificationService) {
        this.offerRepository = offerRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<Offer> activeOffers() {
        LocalDateTime now = LocalDateTime.now();
        return offerRepository.findByActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAsc(now, now);
    }

    @Transactional(readOnly = true)
    public List<Offer> allOffers() {
        return offerRepository.findAllByOrderByStartsAtDesc();
    }

    @Transactional
    public Offer create(OfferData data) {
        validateDates(data);
        Offer offer = offerRepository.save(new Offer(
                data.name(), data.description(), data.discountType(), data.discountValue(),
                data.startsAt(), data.endsAt(), data.eligibility(), data.active()));
        notificationService.offerPublished(offer);
        return offer;
    }

    @Transactional
    public Offer update(Long offerId, OfferData data) {
        validateDates(data);
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found."));
        boolean wasActive = offer.isActive();
        offer.update(
                data.name(), data.description(), data.discountType(), data.discountValue(),
                data.startsAt(), data.endsAt(), data.eligibility(), data.active());
        if (!wasActive) {
            notificationService.offerPublished(offer);
        }
        return offer;
    }

    private static void validateDates(OfferData data) {
        if (!data.endsAt().isAfter(data.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer end date must be after its start date.");
        }
        if (data.discountType() == OfferDiscountType.PERCENT
                && data.discountValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage discounts cannot exceed 100%.");
        }
    }

    public record OfferData(
            String name,
            String description,
            OfferDiscountType discountType,
            java.math.BigDecimal discountValue,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String eligibility,
            boolean active) {
    }
}
