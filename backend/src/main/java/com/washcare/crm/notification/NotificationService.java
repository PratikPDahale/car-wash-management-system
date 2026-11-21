package com.washcare.crm.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.washcare.crm.auth.UserAccount;
import com.washcare.crm.auth.UserAccountRepository;
import com.washcare.crm.auth.UserRole;
import com.washcare.crm.billing.Invoice;
import com.washcare.crm.booking.Booking;
import com.washcare.crm.booking.BookingStatus;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.retention.Offer;

@Service
public class NotificationService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final ZoneId SHOP_ZONE = ZoneId.systemDefault();

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserAccountRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository preferenceRepository,
            UserAccountRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void bookingCreated(Booking booking) {
        notifyCustomer(
                booking.getCustomer(),
                NotificationCategory.BOOKING,
                NotificationPriority.IMPORTANT,
                "Booking confirmed",
                "Your " + booking.getService().getName() + " booking for "
                        + booking.getBookingDate() + " at " + booking.getStartTime() + " is confirmed.",
                booking.getId());
        notifyShop(
                NotificationCategory.BOOKING,
                NotificationPriority.IMPORTANT,
                "New shop booking",
                booking.getCustomer().getName() + " booked " + booking.getService().getName()
                        + " for " + booking.getBookingDate() + " at " + booking.getStartTime() + ".",
                booking.getId());
    }

    @Transactional
    public void bookingStatusChanged(Booking booking, BookingStatus status) {
        notifyCustomer(
                booking.getCustomer(),
                NotificationCategory.BOOKING,
                NotificationPriority.IMPORTANT,
                status == BookingStatus.CANCELLED ? "Booking cancelled" : "Booking status updated",
                "Your booking for " + booking.getService().getName() + " on " + booking.getBookingDate()
                        + " is now " + status.name().toLowerCase().replace('_', ' ') + ".",
                booking.getId());
        notifyShop(
                NotificationCategory.BOOKING,
                NotificationPriority.IMPORTANT,
                "Booking status changed",
                "Booking #" + booking.getId() + " for " + booking.getCustomer().getName()
                        + " is now " + status.name().toLowerCase().replace('_', ' ') + ".",
                booking.getId());
    }

    @Transactional
    public void paymentRecorded(Invoice invoice, boolean settled) {
        Booking booking = invoice.getBooking();
        notifyCustomer(
                booking.getCustomer(),
                NotificationCategory.PAYMENT,
                NotificationPriority.IMPORTANT,
                settled ? "Invoice paid in full" : "Payment recorded",
                settled
                        ? "Invoice " + invoice.getInvoiceNo() + " is fully paid."
                        : "A payment was recorded for invoice " + invoice.getInvoiceNo() + ".",
                booking.getId());
        notifyShop(
                NotificationCategory.PAYMENT,
                NotificationPriority.IMPORTANT,
                settled ? "Invoice settled" : "Payment received",
                "A payment for " + booking.getCustomer().getName() + "'s invoice "
                        + invoice.getInvoiceNo() + (settled ? " settled the balance." : " was recorded."),
                booking.getId());
    }

    @Transactional
    public void loyaltyEarned(Customer customer, int points) {
        notifyCustomer(
                customer,
                NotificationCategory.LOYALTY,
                NotificationPriority.NORMAL,
                "Loyalty points earned",
                "You earned " + points + " loyalty points for paying an invoice in full.",
                null);
    }

    @Transactional
    public void loyaltyRedeemed(Customer customer, int points, Long bookingId) {
        notifyCustomer(
                customer,
                NotificationCategory.LOYALTY,
                NotificationPriority.NORMAL,
                "Loyalty points redeemed",
                points + " loyalty points were applied to your invoice.",
                bookingId);
    }

    @Transactional
    public void offerPublished(Offer offer) {
        if (!offer.isActive()) {
            return;
        }
        String details = offer.getDescription() == null || offer.getDescription().isBlank()
                ? "Check the offer terms in Retention."
                : offer.getDescription();
        for (UserAccount customer : userRepository.findByRoleAndStatus(UserRole.CUSTOMER, ACTIVE_STATUS)) {
            create(customer, NotificationCategory.OFFER, NotificationPriority.NORMAL,
                    "A new shop offer is available", offer.getName() + ": " + details, "Retention", offer.getId());
        }
    }

    @Transactional
    public void returnReminder(Customer customer, Booking booking) {
        notifyCustomer(
                customer,
                NotificationCategory.REMINDER,
                NotificationPriority.NORMAL,
                "Time for another visit?",
                "It has been about 90 days since your " + booking.getService().getName()
                        + " visit. We’d be happy to see you again.",
                booking.getId());
    }

    @Transactional
    public void reviewReceived(Customer customer, Long bookingId, String serviceName) {
        notifyShop(
                NotificationCategory.REVIEW,
                NotificationPriority.NORMAL,
                "New customer review",
                customer.getName() + " left a review for " + serviceName + ".",
                bookingId);
    }

    @Transactional
    public void welcome(UserAccount user) {
        create(user, NotificationCategory.SYSTEM, NotificationPriority.IMPORTANT,
                "Welcome to WashCare", "Your account is ready. Explore your shop workspace to get started.",
                "Overview", null);
    }

    @Transactional
    public void announcement(UserAccount author, String title, String message) {
        List<UserAccount> recipients = userRepository.findByRoleInAndStatus(
                List.of(UserRole.CUSTOMER, UserRole.STAFF), ACTIVE_STATUS);
        for (UserAccount recipient : recipients) {
            create(recipient, NotificationCategory.ANNOUNCEMENT, NotificationPriority.NORMAL,
                    title, message, "Overview", null);
        }
        create(author, NotificationCategory.ANNOUNCEMENT, NotificationPriority.NORMAL,
                "Announcement sent", "Your announcement “" + title + "” was sent to customers and staff.",
                "Overview", null);
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(Long userId, int limit) {
        return notificationRepository.findByUserIdAndAvailableAtLessThanEqualOrderByCreatedAtDesc(
                userId, Instant.now(), org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNullAndAvailableAtLessThanEqual(
                userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public NotificationPreference preferences(Long userId, UserAccount user) {
        return preferenceRepository.findById(userId).orElseGet(() -> new NotificationPreference(user));
    }

    @Transactional
    public NotificationPreference updatePreferences(
            Long userId,
            UserAccount user,
            Set<NotificationCategory> enabled,
            LocalTime quietStart,
            LocalTime quietEnd,
            boolean globalMuted) {
        NotificationPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> new NotificationPreference(user));
        preference.update(enabled, quietStart, quietEnd, globalMuted);
        return preferenceRepository.save(preference);
    }

    @Transactional
    public Notification requireOwned(Long notificationId, Long userId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found."));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now(), Instant.now());
    }

    @Transactional
    public void delete(Notification notification) {
        notificationRepository.delete(notification);
    }

    private void notifyCustomer(
            Customer customer,
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String message,
            Long targetId) {
        if (customer.getUser() != null) {
            create(customer.getUser(), category, priority, title, message,
                    category == NotificationCategory.LOYALTY || category == NotificationCategory.OFFER
                            || category == NotificationCategory.REMINDER ? "Retention" : "Bookings",
                    targetId);
        }
    }

    private void notifyShop(
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String message,
            Long targetId) {
        for (UserAccount recipient : userRepository.findByRoleInAndStatus(
                List.of(UserRole.OWNER, UserRole.STAFF), ACTIVE_STATUS)) {
            create(recipient, category, priority, title, message, "Bookings", targetId);
        }
    }

    private void create(
            UserAccount recipient,
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String message,
            String targetSection,
            Long targetId) {
        NotificationPreference preference = preferenceRepository.findById(recipient.getId()).orElse(null);
        if (preference != null && !preference.isEnabled(category)) {
            return;
        }
        if (preference != null && preference.isGlobalMuted() && priority != NotificationPriority.IMPORTANT) {
            return;
        }
        Instant now = Instant.now();
        Instant availableAt = nextAvailableAt(now, preference);
        notificationRepository.save(new Notification(
                recipient, category, priority, title, message, targetSection, targetId, availableAt));
    }

    private static Instant nextAvailableAt(Instant now, NotificationPreference preference) {
        if (preference == null
                || preference.getQuietHoursStart() == null
                || preference.getQuietHoursEnd() == null) {
            return now;
        }
        LocalDateTime localNow = LocalDateTime.ofInstant(now, SHOP_ZONE);
        LocalTime time = localNow.toLocalTime();
        LocalTime start = preference.getQuietHoursStart();
        LocalTime end = preference.getQuietHoursEnd();
        boolean inQuietHours = start.isBefore(end)
                ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end);
        if (!inQuietHours) {
            return now;
        }
        LocalDate date = localNow.toLocalDate();
        if (start.isAfter(end) && !time.isBefore(start)) {
            date = date.plusDays(1);
        }
        return date.atTime(end).atZone(SHOP_ZONE).toInstant();
    }
}
