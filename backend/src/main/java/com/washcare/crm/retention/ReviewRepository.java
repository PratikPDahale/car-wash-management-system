package com.washcare.crm.retention;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    List<Review> findAllByOrderByCreatedAtDesc();

    @Query("select review from Review review where review.customer.user.id = :userId order by review.createdAt desc")
    List<Review> findCustomerReviews(@Param("userId") Long userId);
}
