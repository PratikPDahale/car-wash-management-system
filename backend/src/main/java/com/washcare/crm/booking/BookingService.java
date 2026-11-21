package com.washcare.crm.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.UserAccount;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;
import com.washcare.crm.customer.Vehicle;
import com.washcare.crm.customer.VehicleRepository;
import com.washcare.crm.billing.BillingService;
import com.washcare.crm.notification.NotificationService;
import com.washcare.crm.service.ServiceOffering;
import com.washcare.crm.service.ServiceOfferingRepository;

@Service
public class BookingService {

    private static final Long SHOP_SCHEDULE_ID = 1L;
    private static final ZoneId SHOP_TIME_ZONE = ZoneId.systemDefault();
    private static final Map<BookingStatus, EnumSet<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED),
            BookingStatus.CHECKED_IN, EnumSet.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED),
            BookingStatus.IN_PROGRESS, EnumSet.of(BookingStatus.COMPLETED));

    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final ShopScheduleRepository scheduleRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final com.washcare.crm.auth.UserAccountRepository userRepository;
    private final BillingService billingService;
    private final NotificationService notificationService;

    public BookingService(
            BookingRepository bookingRepository,
            BookingStatusHistoryRepository historyRepository,
            ShopScheduleRepository scheduleRepository,
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository,
            ServiceOfferingRepository serviceRepository,
            com.washcare.crm.auth.UserAccountRepository userRepository,
            BillingService billingService,
            NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.historyRepository = historyRepository;
        this.scheduleRepository = scheduleRepository;
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.billingService = billingService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<LocalTime> availableSlots(LocalDate date, Long serviceId) {
        ShopSchedule schedule = getSchedule();
        ServiceOffering service = getActiveService(serviceId);
        return getAvailableSlots(schedule, date, service, bookingRepository.findByBookingDateOrderByStartTimeAsc(date), null);
    }

    @Transactional
    public Booking createCustomerBooking(
            Long customerUserId,
            Long serviceId,
            Long vehicleId,
            LocalDate date,
            LocalTime startTime,
            String notes) {
        ShopSchedule schedule = lockSchedule();
        Customer customer = customerRepository.findByUserId(customerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .filter(item -> Objects.equals(item.getCustomer().getId(), customer.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found in your profile."));
        return createBooking(schedule, customer, vehicle, serviceId, date, startTime, notes, BookingSource.ONLINE,
                getAccount(customerUserId));
    }

    @Transactional
    public Booking createWalkInBooking(
            Long staffUserId,
            Long customerId,
            Long vehicleId,
            Long serviceId,
            LocalDate date,
            LocalTime startTime,
            String notes) {
        ShopSchedule schedule = lockSchedule();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .filter(item -> Objects.equals(item.getCustomer().getId(), customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a vehicle belonging to this customer."));
        return createBooking(schedule, customer, vehicle, serviceId, date, startTime, notes, BookingSource.WALK_IN,
                getAccount(staffUserId));
    }

    @Transactional
    public Booking cancelCustomerBooking(Long customerUserId, Long bookingId) {
        ShopSchedule schedule = lockSchedule();
        Customer customer = customerRepository.findByUserId(customerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
        Booking booking = bookingRepository.findByIdAndCustomerId(bookingId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found in your history."));
        if (!booking.canCustomerCancel(schedule)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking can no longer be cancelled.");
        }
        changeStatus(booking, BookingStatus.CANCELLED, getAccount(customerUserId));
        return booking;
    }

    @Transactional
    public Booking updateStatus(Long staffUserId, Long bookingId, BookingStatus nextStatus) {
        ShopSchedule schedule = lockSchedule();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        EnumSet<BookingStatus> allowed = ALLOWED_TRANSITIONS.get(booking.getStatus());
        if (allowed == null || !allowed.contains(nextStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking cannot move from " + booking.getStatus() + " to " + nextStatus + ".");
        }
        changeStatus(booking, nextStatus, getAccount(staffUserId));
        if (nextStatus == BookingStatus.COMPLETED) {
            billingService.issueForCompletedBooking(booking);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> customerBookings(Long customerUserId) {
        Customer customer = customerRepository.findByUserId(customerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
        return bookingRepository.findByCustomerIdOrderByBookingDateDescStartTimeDesc(customer.getId());
    }

    @Transactional(readOnly = true)
    public List<Booking> shopBookings(LocalDate date) {
        if (date != null) {
            return bookingRepository.findByBookingDateOrderByStartTimeAsc(date);
        }
        return bookingRepository.findAllByOrderByBookingDateDescStartTimeDesc();
    }

    private Booking createBooking(
            ShopSchedule schedule,
            Customer customer,
            Vehicle vehicle,
            Long serviceId,
            LocalDate date,
            LocalTime startTime,
            String notes,
            BookingSource source,
            UserAccount actor) {
        ServiceOffering service = getActiveService(serviceId);
        List<Booking> existingBookings = bookingRepository.findByBookingDateOrderByStartTimeAsc(date);
        List<LocalTime> availableSlots = getAvailableSlots(schedule, date, service, existingBookings, vehicle.getId());
        if (!availableSlots.contains(startTime)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This start time is unavailable. Refresh the available slots and choose another time.");
        }

        Booking booking = bookingRepository.save(new Booking(
                customer, vehicle, service, date, startTime, source, normalizeNotes(notes)));
        historyRepository.save(new BookingStatusHistory(booking, null, BookingStatus.CONFIRMED, actor));
        notificationService.bookingCreated(booking);
        return booking;
    }

    private List<LocalTime> getAvailableSlots(
            ShopSchedule schedule,
            LocalDate date,
            ServiceOffering service,
            List<Booking> existingBookings,
            Long vehicleId) {
        if (date == null || date.isBefore(LocalDate.now(SHOP_TIME_ZONE)) || !schedule.isOpenOn(date.getDayOfWeek())) {
            return List.of();
        }

        List<LocalTime> available = new ArrayList<>();
        LocalTime start = schedule.getOpeningTime();
        LocalTime closing = schedule.getClosingTime();
        while (!start.plusMinutes(service.getEstimatedDurationMinutes()).isAfter(closing)) {
            if (date.atTime(start).atZone(SHOP_TIME_ZONE).toInstant().isAfter(Instant.now())
                    && hasCapacity(start, service, existingBookings, schedule.getCapacity(), vehicleId)) {
                available.add(start);
            }
            start = start.plusMinutes(schedule.getSlotIntervalMinutes());
        }
        return available;
    }

    private boolean hasCapacity(
            LocalTime candidateStart,
            ServiceOffering service,
            List<Booking> existingBookings,
            int capacity,
            Long vehicleId) {
        LocalTime candidateEnd = candidateStart.plusMinutes(service.getEstimatedDurationMinutes());
        List<Interval> intervals = new ArrayList<>();
        intervals.add(new Interval(candidateStart, candidateEnd));
        for (Booking existing : existingBookings) {
            if (existing.getStatus() == BookingStatus.CANCELLED || existing.getStatus() == BookingStatus.COMPLETED) {
                continue;
            }
            LocalTime existingStart = existing.getStartTime();
            LocalTime existingEnd = existingStart.plusMinutes(existing.getService().getEstimatedDurationMinutes());
            if (existingStart.isBefore(candidateEnd) && candidateStart.isBefore(existingEnd)) {
                if (vehicleId != null && existing.getVehicle().getId().equals(vehicleId)) {
                    return false;
                }
                intervals.add(new Interval(existingStart, existingEnd));
            }
        }

        List<LocalTime> changePoints = intervals.stream()
                .map(Interval::start)
                .sorted(Comparator.naturalOrder())
                .toList();
        for (LocalTime point : changePoints) {
            long inProgress = intervals.stream()
                    .filter(interval -> !interval.start().isAfter(point) && interval.end().isAfter(point))
                    .count();
            if (inProgress > capacity) {
                return false;
            }
        }
        return true;
    }

    private void changeStatus(Booking booking, BookingStatus status, UserAccount actor) {
        BookingStatus previousStatus = booking.getStatus();
        booking.transitionTo(status);
        bookingRepository.save(booking);
        historyRepository.save(new BookingStatusHistory(booking, previousStatus, status, actor));
        notificationService.bookingStatusChanged(booking, status);
    }

    private ShopSchedule lockSchedule() {
        return scheduleRepository.lockById(SHOP_SCHEDULE_ID)
                .orElseThrow(() -> new IllegalStateException("Shop scheduling settings are not configured."));
    }

    private ShopSchedule getSchedule() {
        return scheduleRepository.findById(SHOP_SCHEDULE_ID)
                .orElseThrow(() -> new IllegalStateException("Shop scheduling settings are not configured."));
    }

    private ServiceOffering getActiveService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active service not found."));
    }

    private UserAccount getAccount(Long accountId) {
        return userRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));
    }

    private static String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }

    private record Interval(LocalTime start, LocalTime end) {
    }
}
