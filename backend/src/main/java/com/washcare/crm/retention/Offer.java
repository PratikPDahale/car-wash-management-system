package com.washcare.crm.retention;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "offers")
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private OfferDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(length = 500)
    private String eligibility;

    @Column(nullable = false)
    private boolean active;

    protected Offer() {
    }

    public Offer(
            String name,
            String description,
            OfferDiscountType discountType,
            BigDecimal discountValue,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String eligibility,
            boolean active) {
        update(name, description, discountType, discountValue, startsAt, endsAt, eligibility, active);
    }

    public void update(
            String name,
            String description,
            OfferDiscountType discountType,
            BigDecimal discountValue,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String eligibility,
            boolean active) {
        this.name = name.trim();
        this.description = normalize(description);
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.eligibility = normalize(eligibility);
        this.active = active;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public OfferDiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public String getEligibility() { return eligibility; }
    public boolean isActive() { return active; }
}
