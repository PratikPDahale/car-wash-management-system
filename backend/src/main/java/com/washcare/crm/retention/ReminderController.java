package com.washcare.crm.retention;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public List<ReminderResponse> getMyReminders(Authentication authentication) {
        return reminderService.customerReminders(currentUser(authentication).id()).stream()
                .map(reminder -> new ReminderResponse(
                        reminder.getId(), reminder.getBooking().getId(),
                        reminder.getBooking().getService().getName(),
                        reminder.getScheduledAt(), reminder.getStatus().name(),
                        reminder.getChannel(), reminder.getSentAt()))
                .toList();
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record ReminderResponse(
            Long id, Long bookingId, String serviceName, LocalDateTime scheduledAt,
            String status, String channel, Instant sentAt) { }
}
