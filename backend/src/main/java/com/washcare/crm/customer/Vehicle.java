package com.washcare.crm.customer;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "registration_no", nullable = false, unique = true, length = 20)
    private String registrationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Vehicle() {
    }

    public Vehicle(Customer customer, String registrationNo, VehicleType type) {
        this.customer = customer;
        this.registrationNo = registrationNo.trim().toUpperCase(Locale.ROOT);
        this.type = type;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public VehicleType getType() {
        return type;
    }

    public enum VehicleType {
        HATCHBACK,
        SEDAN,
        SUV,
        BIKE,
        OTHER
    }
}
