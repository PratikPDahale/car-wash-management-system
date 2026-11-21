package com.washcare.crm.billing;

import java.math.BigDecimal;
import java.time.Instant;

import com.washcare.crm.auth.UserAccount;

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
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private UserAccount recordedBy;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    protected Payment() {
    }

    public Payment(Invoice invoice, BigDecimal amount, PaymentMethod method, UserAccount recordedBy, String idempotencyKey) {
        this.invoice = invoice;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PAID;
        this.recordedBy = recordedBy;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void setPaidAt() {
        if (paidAt == null) {
            paidAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public UserAccount getRecordedBy() {
        return recordedBy;
    }
}
