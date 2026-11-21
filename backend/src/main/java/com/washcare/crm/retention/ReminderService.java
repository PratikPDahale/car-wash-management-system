package com.washcare.crm.retention;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.washcare.crm.booking.Booking;
import com.washcare.crm.booking.BookingRepository;
import com.washcare.crm.booking.BookingStatus;
import com.washcare.crm.booking.BookingStatusHistoryRepository;
import com.washcare.crm.booking.BookingStatusHistory;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;
import com.washcare.crm.notification.NotificationService;

@Service
public class ReminderService {

    private static final int REMINDER_INTERVAL_DAYS = 90;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationService notificationService;

    public ReminderService(
            CustomerRepository customerRepository,
            BookingRepository bookingRepository,
            BookingStatusHistoryRepository historyRepository,
            ReminderRepository reminderRepository,
            NotificationService notificationService) {
        this.customerRepository = customerRepository;
        this.bookingRepository = bookingRepository;
        this.historyRepository = historyRepository;
        this.reminderRepository = reminderRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<Reminder> customerReminders(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Customer profile not found."));
        List<Booking> completedBookings = bookingRepository.findByCustomerIdAndStatusOrderByBookingDateDescStartTimeDesc(
                customer.getId(), BookingStatus.COMPLETED);
        Booking latestCompleted = completedBookings.isEmpty() ? null : completedBookings.get(0);
        List<Reminder> reminders = reminderRepository.findByCustomerIdOrderByScheduledAtDesc(customer.getId());
        return updateReminders(customer, latestCompleted, reminders);
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void generateDueReminders() {
        Map<Long, Booking> latestByCustomer = new LinkedHashMap<>();
        for (Booking booking : bookingRepository.findAllByStatusOrderByBookingDateDescStartTimeDesc(BookingStatus.COMPLETED)) {
            if (booking.getCustomer().getUser() != null) {
                latestByCustomer.putIfAbsent(booking.getCustomer().getId(), booking);
            }
        }
        for (Booking latestCompleted : latestByCustomer.values()) {
            Customer customer = latestCompleted.getCustomer();
            List<Reminder> reminders = reminderRepository.findByCustomerIdOrderByScheduledAtDesc(customer.getId());
            updateReminders(customer, latestCompleted, reminders);
        }
    }

    private List<Reminder> updateReminders(Customer customer, Booking latestCompleted, List<Reminder> reminders) {
        for (Reminder reminder : reminders) {
            if (reminder.isScheduled() && (latestCompleted == null
                    || !reminder.belongsToBooking(latestCompleted.getId()))) {
                reminder.cancel();
            }
        }
        if (latestCompleted != null) {
            BookingStatusHistory completedAt = historyRepository
                    .findFirstByBookingIdAndNewStatusOrderByChangedAtDesc(latestCompleted.getId(), BookingStatus.COMPLETED)
                    .orElse(null);
            if (completedAt != null) {
                LocalDateTime scheduledAt = completedAt.getChangedAt()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        .plusDays(REMINDER_INTERVAL_DAYS);
                if (!scheduledAt.isAfter(LocalDateTime.now())
                        && reminders.stream().noneMatch(item -> item.belongsToBooking(latestCompleted.getId()))) {
                    reminders.add(reminderRepository.save(new Reminder(customer, latestCompleted, scheduledAt)));
                }
            }
        }
        for (Reminder reminder : reminders) {
            if (!reminder.getScheduledAt().isAfter(LocalDateTime.now())) {
                boolean wasScheduled = reminder.isScheduled();
                reminder.markDeliveredInApp();
                if (wasScheduled && reminder.getStatus() == ReminderStatus.SENT) {
                    notificationService.returnReminder(customer, reminder.getBooking());
                }
            }
        }
        return reminders.stream()
                .filter(reminder -> reminder.getStatus() != ReminderStatus.CANCELLED)
                .toList();
    }
}
