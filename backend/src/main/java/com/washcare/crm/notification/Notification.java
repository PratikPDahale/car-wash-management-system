package com.washcare.crm.notification;

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
import jakarta.persistence.Index;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_available_created", columnList = "user_id,available_at,created_at"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id,read_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationPriority priority;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "target_section", nullable = false, length = 30)
    private String targetSection;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(
            UserAccount user,
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String message,
            String targetSection,
            Long targetId,
            Instant availableAt) {
        this.user = user;
        this.category = category;
        this.priority = priority;
        this.title = title;
        this.message = message;
        this.targetSection = targetSection;
        this.targetId = targetId;
        this.availableAt = availableAt;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public void markUnread() {
        readAt = null;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public NotificationCategory getCategory() { return category; }
    public NotificationPriority getPriority() { return priority; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTargetSection() { return targetSection; }
    public Long getTargetId() { return targetId; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
