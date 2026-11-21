package com.washcare.crm.retention;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAsc(
            LocalDateTime now,
            LocalDateTime nowAgain);

    List<Offer> findAllByOrderByStartsAtDesc();
}
