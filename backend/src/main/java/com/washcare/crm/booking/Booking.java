package com.washcare.crm.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.washcare.crm.auth.UserAccount;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.Vehicle;
import com.washcare.crm.service.ServiceOffering;

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
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(
            Customer customer,
            Vehicle vehicle,
            ServiceOffering service,
            LocalDate bookingDate,
            LocalTime startTime,
            BookingSource source,
            String notes) {
        this.customer = customer;
        this.vehicle = vehicle;
        this.service = service;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.source = source;
        this.status = BookingStatus.CONFIRMED;
        this.priceSnapshot = service.getPrice();
        this.notes = notes;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void transitionTo(BookingStatus nextStatus) {
        this.status = nextStatus;
    }

    public boolean canCustomerCancel(ShopSchedule schedule) {
        return status != BookingStatus.CANCELLED
                && status != BookingStatus.COMPLETED
                && status != BookingStatus.IN_PROGRESS
                && status != BookingStatus.CHECKED_IN
                && LocalDateTime.now(ZoneId.systemDefault()).isBefore(bookingDate.atTime(startTime)
                        .minusMinutes(schedule.getCancellationCutoffMinutes())
                        );
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ServiceOffering getService() {
        return service;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public BookingSource getSource() {
        return source;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public String getNotes() {
        return notes;
    }

    public UserAccount getCustomerUser() {
        return customer.getUser();
    }
}
