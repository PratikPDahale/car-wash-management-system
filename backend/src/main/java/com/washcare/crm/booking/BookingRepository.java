package com.washcare.crm.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"customer", "vehicle", "service"})
    @Query("""
            select booking from Booking booking
            where lower(booking.customer.name) like lower(concat('%', :query, '%'))
               or booking.customer.phone like concat('%', :query, '%')
               or lower(booking.vehicle.registrationNo) like lower(concat('%', :query, '%'))
               or lower(booking.service.name) like lower(concat('%', :query, '%'))
               or (:bookingId is not null and booking.id = :bookingId)
            order by booking.bookingDate desc, booking.startTime desc
            """)
    Page<Booking> searchForShop(
            @Param("query") String query,
            @Param("bookingId") Long bookingId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "vehicle", "service"})
    @Query("""
            select booking from Booking booking
            where booking.customer.user.id = :userId
              and (
                   lower(booking.customer.name) like lower(concat('%', :query, '%'))
                   or booking.customer.phone like concat('%', :query, '%')
                   or lower(booking.vehicle.registrationNo) like lower(concat('%', :query, '%'))
                   or lower(booking.service.name) like lower(concat('%', :query, '%'))
                   or (:bookingId is not null and booking.id = :bookingId)
              )
            order by booking.bookingDate desc, booking.startTime desc
            """)
    Page<Booking> searchForCustomer(
            @Param("query") String query,
            @Param("bookingId") Long bookingId,
            @Param("userId") Long userId,
            Pageable pageable);

    List<Booking> findByBookingDateOrderByStartTimeAsc(LocalDate bookingDate);

    List<Booking> findByCustomerIdOrderByBookingDateDescStartTimeDesc(Long customerId);

    List<Booking> findAllByOrderByBookingDateDescStartTimeDesc();

    Optional<Booking> findByIdAndCustomerId(Long id, Long customerId);

    List<Booking> findByCustomerIdAndStatusOrderByBookingDateDescStartTimeDesc(
            Long customerId,
            BookingStatus status);

    List<Booking> findAllByStatusOrderByBookingDateDescStartTimeDesc(BookingStatus status);
}
