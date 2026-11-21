package com.washcare.crm.retention;

import java.time.Instant;

import com.washcare.crm.booking.Booking;
import com.washcare.crm.billing.Invoice;
import com.washcare.crm.customer.Customer;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "loyalty_transactions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_loyalty_invoice_transaction",
                columnNames = {"invoice_id", "transaction_type"})
})
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private LoyaltyTransactionType transactionType;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoyaltyTransaction() {
    }

    public LoyaltyTransaction(
            Customer customer,
            Booking booking,
            Invoice invoice,
            int pointsDelta,
            LoyaltyTransactionType transactionType,
            String reason) {
        this.customer = customer;
        this.booking = booking;
        this.invoice = invoice;
        this.pointsDelta = pointsDelta;
        this.transactionType = transactionType;
        this.reason = reason;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Booking getBooking() { return booking; }
    public Invoice getInvoice() { return invoice; }
    public int getPointsDelta() { return pointsDelta; }
    public LoyaltyTransactionType getTransactionType() { return transactionType; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
