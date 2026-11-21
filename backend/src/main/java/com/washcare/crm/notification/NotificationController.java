package com.washcare.crm.notification;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;
import com.washcare.crm.auth.UserAccount;
import com.washcare.crm.auth.UserAccountRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'CUSTOMER')")
public class NotificationController {

    private static final int INBOX_LIMIT = 50;

    private final NotificationService notificationService;
    private final UserAccountRepository userRepository;

    public NotificationController(
            NotificationService notificationService,
            UserAccountRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public InboxResponse inbox(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return new InboxResponse(
                notificationService.latest(userId, INBOX_LIMIT).stream()
                        .map(NotificationController::toResponse)
                        .toList(),
                notificationService.unreadCount(userId),
                INBOX_LIMIT);
    }

    @PutMapping("/{notificationId}/read")
    @Transactional
    public NotificationResponse markRead(Authentication authentication, @PathVariable Long notificationId) {
        Notification notification = notificationService.requireOwned(notificationId, currentUserId(authentication));
        notification.markRead();
        return toResponse(notification);
    }

    @PutMapping("/{notificationId}/unread")
    @Transactional
    public NotificationResponse markUnread(Authentication authentication, @PathVariable Long notificationId) {
        Notification notification = notificationService.requireOwned(notificationId, currentUserId(authentication));
        notification.markUnread();
        return toResponse(notification);
    }

    @PutMapping("/read-all")
    public MarkAllResponse markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUserId(authentication));
        return new MarkAllResponse(0);
    }

    @DeleteMapping("/{notificationId}")
    @Transactional
    public void delete(Authentication authentication, @PathVariable Long notificationId) {
        notificationService.delete(notificationService.requireOwned(notificationId, currentUserId(authentication)));
    }

    @GetMapping("/preferences")
    @Transactional(readOnly = true)
    public PreferenceResponse preferences(Authentication authentication) {
        UserAccount user = currentUser(authentication);
        return toResponse(notificationService.preferences(user.getId(), user));
    }

    @PutMapping("/preferences")
    @Transactional
    public PreferenceResponse updatePreferences(
            Authentication authentication,
            @Valid @RequestBody PreferenceRequest request) {
        if ((request.quietHoursStart() == null) != (request.quietHoursEnd() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Set both quiet-hours times or leave both empty.");
        }
        if (request.quietHoursStart() != null
                && request.quietHoursStart().equals(request.quietHoursEnd())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quiet-hours start and end times must be different.");
        }
        UserAccount user = currentUser(authentication);
        Set<NotificationCategory> enabled = Set.copyOf(request.enabledCategories());
        return toResponse(notificationService.updatePreferences(
                user.getId(),
                user,
                enabled,
                request.quietHoursStart(),
                request.quietHoursEnd(),
                request.globalMuted()));
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public AnnouncementResponse sendAnnouncement(
            Authentication authentication,
            @Valid @RequestBody AnnouncementRequest request) {
        UserAccount author = currentUser(authentication);
        notificationService.announcement(author, request.title().trim(), request.message().trim());
        return new AnnouncementResponse("Announcement sent to customers and staff.");
    }

    private UserAccount currentUser(Authentication authentication) {
        return userRepository.findById(currentUserId(authentication))
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is no longer active."));
    }

    private static Long currentUserId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).id();
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getCategory().name(),
                notification.getPriority().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetSection(),
                notification.getTargetId(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    private static PreferenceResponse toResponse(NotificationPreference preference) {
        return new PreferenceResponse(
                preference.getEnabledCategories().stream().map(Enum::name).sorted().toList(),
                preference.getQuietHoursStart(),
                preference.getQuietHoursEnd(),
                preference.isGlobalMuted());
    }

    public record InboxResponse(List<NotificationResponse> items, long unreadCount, int limit) { }

    public record NotificationResponse(
            Long id,
            String category,
            String priority,
            String title,
            String message,
            String targetSection,
            Long targetId,
            Instant createdAt,
            Instant readAt) { }

    public record MarkAllResponse(int unreadCount) { }

    public record PreferenceResponse(
            List<String> enabledCategories,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            boolean globalMuted) { }

    public record PreferenceRequest(
            @NotNull List<@NotNull NotificationCategory> enabledCategories,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            boolean globalMuted) { }

    public record AnnouncementRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String message) { }

    public record AnnouncementResponse(String message) { }
}
