package com.washcare.crm.billing;

import java.math.BigDecimal;
import java.time.Instant;

import com.washcare.crm.booking.Booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoices_invoice_no", columnNames = "invoice_no")
})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "invoice_no", nullable = false, length = 40)
    private String invoiceNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected Invoice() {
    }

    public Invoice(Booking booking, String invoiceNo) {
        this.booking = booking;
        this.invoiceNo = invoiceNo;
        this.subtotal = booking.getPriceSnapshot();
        this.discount = BigDecimal.ZERO;
        this.totalAmount = subtotal.subtract(discount);
    }

    @PrePersist
    void setIssuedAt() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void applyLoyaltyDiscount(BigDecimal loyaltyDiscount) {
        this.discount = this.discount.add(loyaltyDiscount);
        this.totalAmount = this.subtotal.subtract(this.discount);
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
