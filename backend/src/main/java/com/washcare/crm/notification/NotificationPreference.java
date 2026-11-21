package com.washcare.crm.notification;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.washcare.crm.auth.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "enabled_categories", nullable = false, length = 255)
    private String enabledCategories;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "global_muted", nullable = false)
    private boolean globalMuted;

    protected NotificationPreference() {
    }

    public NotificationPreference(UserAccount user) {
        this.user = user;
        this.enabledCategories = EnumSet.allOf(NotificationCategory.class).stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public void update(
            Set<NotificationCategory> enabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            boolean globalMuted) {
        this.enabledCategories = enabled.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.globalMuted = globalMuted;
    }

    public boolean isEnabled(NotificationCategory category) {
        return Arrays.asList(enabledCategories.split(",")).contains(category.name());
    }

    public Set<NotificationCategory> getEnabledCategories() {
        if (enabledCategories == null || enabledCategories.isBlank()) {
            return EnumSet.noneOf(NotificationCategory.class);
        }
        return Arrays.stream(enabledCategories.split(","))
                .map(NotificationCategory::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationCategory.class)));
    }

    public Long getUserId() { return userId; }
    public UserAccount getUser() { return user; }
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public boolean isGlobalMuted() { return globalMuted; }
}
