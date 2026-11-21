package com.washcare.crm.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.Vehicle;
import com.washcare.crm.service.ServiceOffering;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ShopScheduleRepository scheduleRepository;

    public BookingController(BookingService bookingService, ShopScheduleRepository scheduleRepository) {
        this.bookingService = bookingService;
        this.scheduleRepository = scheduleRepository;
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'CUSTOMER')")
    public AvailabilityResponse getAvailability(
            @RequestParam @FutureOrPresent LocalDate date,
            @RequestParam @Positive Long serviceId) {
        ShopSchedule schedule = scheduleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Shop scheduling settings are not configured."));
        return new AvailabilityResponse(
                date,
                schedule.getOpeningTime(),
                schedule.getClosingTime(),
                schedule.getSlotIntervalMinutes(),
                schedule.getCapacity(),
                schedule.getOpenDayValues().stream().sorted().map(Enum::name).toList(),
                bookingService.availableSlots(date, serviceId));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public BookingResponse createCustomerBooking(
            Authentication authentication,
            @Valid @RequestBody CustomerBookingRequest request) {
        AuthenticatedUser user = currentUser(authentication);
        return toResponse(bookingService.createCustomerBooking(
                user.id(),
                request.serviceId(),
                request.vehicleId(),
                request.date(),
                request.startTime(),
                request.notes()));
    }

    @PostMapping("/walk-in")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional
    public BookingResponse createWalkInBooking(
            Authentication authentication,
            @Valid @RequestBody WalkInBookingRequest request) {
        AuthenticatedUser user = currentUser(authentication);
        return toResponse(bookingService.createWalkInBooking(
                user.id(),
                request.customerId(),
                request.vehicleId(),
                request.serviceId(),
                request.date(),
                request.startTime(),
                request.notes()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Authentication authentication) {
        return bookingService.customerBookings(currentUser(authentication).id()).stream().map(this::toResponse).toList();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional(readOnly = true)
    public List<BookingResponse> getShopBookings(@RequestParam(required = false) LocalDate date) {
        return bookingService.shopBookings(date).stream().map(this::toResponse).toList();
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public BookingResponse cancelOwnBooking(Authentication authentication, @PathVariable Long bookingId) {
        return toResponse(bookingService.cancelCustomerBooking(currentUser(authentication).id(), bookingId));
    }

    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional
    public BookingResponse updateBookingStatus(
            Authentication authentication,
            @PathVariable Long bookingId,
            @Valid @RequestBody UpdateStatusRequest request) {
        return toResponse(bookingService.updateStatus(currentUser(authentication).id(), bookingId, request.status()));
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    private BookingResponse toResponse(Booking booking) {
        Customer customer = booking.getCustomer();
        Vehicle vehicle = booking.getVehicle();
        ServiceOffering service = booking.getService();
        return new BookingResponse(
                booking.getId(),
                customer.getId(),
                customer.getName(),
                vehicle.getId(),
                vehicle.getRegistrationNo(),
                service.getId(),
                service.getName(),
                booking.getBookingDate(),
                booking.getStartTime(),
                booking.getStartTime().plusMinutes(service.getEstimatedDurationMinutes()),
                booking.getSource().name(),
                booking.getStatus().name(),
                booking.getPriceSnapshot(),
                booking.getNotes());
    }

    public record CustomerBookingRequest(
            @NotNull @Positive Long serviceId,
            @NotNull @Positive Long vehicleId,
            @NotNull @FutureOrPresent LocalDate date,
            @NotNull LocalTime startTime,
            @Size(max = 500) String notes) {
    }

    public record WalkInBookingRequest(
            @NotNull @Positive Long customerId,
            @NotNull @Positive Long vehicleId,
            @NotNull @Positive Long serviceId,
            @NotNull @FutureOrPresent LocalDate date,
            @NotNull LocalTime startTime,
            @Size(max = 500) String notes) {
    }

    public record UpdateStatusRequest(@NotNull BookingStatus status) {
    }

    public record AvailabilityResponse(
            LocalDate date,
            LocalTime openingTime,
            LocalTime closingTime,
            int slotIntervalMinutes,
            int capacity,
            List<String> openDays,
            List<LocalTime> availableSlots) {
    }

    public record BookingResponse(
            Long id,
            Long customerId,
            String customerName,
            Long vehicleId,
            String registrationNo,
            Long serviceId,
            String serviceName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String source,
            String status,
            java.math.BigDecimal priceSnapshot,
            String notes) {
    }
}
