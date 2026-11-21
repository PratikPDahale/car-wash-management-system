package com.washcare.crm.service;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "services")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Positive
    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;

    @Column(nullable = false)
    private boolean active = true;

    protected ServiceOffering() {
    }

    public ServiceOffering(String name, String description, BigDecimal price, int estimatedDurationMinutes) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void update(String name, String description, BigDecimal price, int estimatedDurationMinutes, boolean active) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.price = price;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.active = active;
    }
}
