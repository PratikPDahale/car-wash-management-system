package com.washcare.crm.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washcare.crm.billing.Payment;
import com.washcare.crm.booking.Booking;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.retention.Review;

public interface AnalyticsRepository extends JpaRepository<Payment, Long> {

    @Query("select coalesce(sum(payment.amount), 0) from Payment payment "
            + "where payment.status = com.washcare.crm.billing.PaymentStatus.PAID "
            + "and payment.paidAt >= :start and payment.paidAt < :end")
    BigDecimal paidRevenue(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query(value = """
            select date(p.paid_at) as revenue_date, sum(p.amount) as revenue
            from payments p
            where p.status = 'PAID' and p.paid_at >= :start and p.paid_at < :end
            group by date(p.paid_at)
            order by date(p.paid_at)
            """, nativeQuery = true)
    List<Object[]> dailyRevenue(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("""
            select service.name, count(booking.id)
            from Booking booking join booking.service service
            where booking.status = com.washcare.crm.booking.BookingStatus.COMPLETED
              and booking.bookingDate >= :start and booking.bookingDate <= :end
            group by service.name
            order by count(booking.id) desc, service.name
            """)
    List<Object[]> popularServices(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select count(booking.id)
            from Booking booking
            where booking.status = com.washcare.crm.booking.BookingStatus.COMPLETED
              and booking.bookingDate >= :start and booking.bookingDate <= :end
            """)
    long completedBookings(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select count(booking.id)
            from Booking booking
            where booking.bookingDate >= :start and booking.bookingDate <= :end
            """)
    long totalBookings(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select count(booking.id) from Booking booking where booking.bookingDate = :date")
    long bookingsOnDate(@Param("date") LocalDate date);

    @Query("select count(customer.id) from Customer customer")
    long customerProfiles();

    @Query("""
            select count(distinct booking.customer.id)
            from Booking booking
            where booking.status = com.washcare.crm.booking.BookingStatus.COMPLETED
              and booking.bookingDate >= :start and booking.bookingDate <= :end
            """)
    long uniqueCompletedCustomers(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
            select booking.customer.id
            from Booking booking
            where booking.status = com.washcare.crm.booking.BookingStatus.COMPLETED
              and booking.bookingDate >= :start and booking.bookingDate <= :end
            group by booking.customer.id
            having count(booking.id) > 1
            """)
    List<Long> repeatCustomerIds(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select avg(review.rating) from Review review where review.createdAt >= :start and review.createdAt < :end")
    Double averageReviewRating(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("select count(review.id) from Review review where review.createdAt >= :start and review.createdAt < :end")
    long reviewCount(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);
}
