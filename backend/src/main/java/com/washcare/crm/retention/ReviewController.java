package com.washcare.crm.retention;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;
import com.washcare.crm.booking.Booking;
import com.washcare.crm.booking.BookingRepository;
import com.washcare.crm.booking.BookingStatus;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;
import com.washcare.crm.notification.NotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final NotificationService notificationService;

    public ReviewController(
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    @Transactional(readOnly = true)
    public List<ReviewResponse> getShopReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream().map(ReviewController::toResponse).toList();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(Authentication authentication) {
        return reviewRepository.findCustomerReviews(currentUser(authentication).id()).stream()
                .map(ReviewController::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ReviewResponse create(Authentication authentication, @Valid @RequestBody CreateReviewRequest request) {
        Customer customer = customerRepository.findByUserId(currentUser(authentication).id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
        Booking booking = bookingRepository.findByIdAndCustomerId(request.bookingId(), customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found in your history."));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can review a service after it is completed.");
        }
        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A review has already been submitted for this booking.");
        }
        Review review = reviewRepository.save(new Review(customer, booking, request.rating(), request.comment()));
        notificationService.reviewReceived(customer, booking.getId(), booking.getService().getName());
        return toResponse(review);
    }

    private static ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(), review.getBooking().getId(), review.getCustomer().getName(),
                review.getBooking().getService().getName(), review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getBooking().getBookingDate());
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record CreateReviewRequest(
            @NotNull Long bookingId,
            @Min(1) @Max(5) int rating,
            @Size(max = 1000) String comment) { }

    public record ReviewResponse(
            Long id, Long bookingId, String customerName, String serviceName, int rating,
            String comment, Instant createdAt, LocalDate serviceDate) { }
}
