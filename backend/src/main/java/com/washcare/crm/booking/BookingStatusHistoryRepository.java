package com.washcare.crm.booking;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    Optional<BookingStatusHistory> findFirstByBookingIdAndNewStatusOrderByChangedAtDesc(
            Long bookingId,
            BookingStatus newStatus);
}
