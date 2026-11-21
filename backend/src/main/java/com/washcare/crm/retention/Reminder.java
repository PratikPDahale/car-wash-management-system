package com.washcare.crm.retention;

import java.time.Instant;
import java.time.LocalDateTime;

import com.washcare.crm.booking.Booking;
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
@Table(name = "reminders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reminders_customer_trigger", columnNames = {"customer_id", "trigger_key"})
})
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "trigger_key", nullable = false, length = 100)
    private String triggerKey;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false, length = 20)
    private String channel = "IN_APP";

    @Column(name = "reminder_type", nullable = false, length = 30)
    private String reminderType = "RETURN_VISIT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.SCHEDULED;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reminder() {
    }

    public Reminder(Customer customer, Booking booking, LocalDateTime scheduledAt) {
        this.customer = customer;
        this.booking = booking;
        this.triggerKey = "POST_VISIT_90D:" + booking.getId();
        this.scheduledAt = scheduledAt;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void markDeliveredInApp() {
        if (status == ReminderStatus.SCHEDULED) {
            status = ReminderStatus.SENT;
            sentAt = Instant.now();
        }
    }

    public void cancel() {
        if (status == ReminderStatus.SCHEDULED) status = ReminderStatus.CANCELLED;
    }

    public boolean belongsToBooking(Long bookingId) {
        return booking.getId().equals(bookingId);
    }

    public boolean isScheduled() {
        return status == ReminderStatus.SCHEDULED;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Booking getBooking() { return booking; }
    public String getTriggerKey() { return triggerKey; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getChannel() { return channel; }
    public String getReminderType() { return reminderType; }
    public ReminderStatus getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
