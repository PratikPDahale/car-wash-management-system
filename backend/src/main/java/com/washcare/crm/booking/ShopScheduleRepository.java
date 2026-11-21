package com.washcare.crm.booking;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ShopScheduleRepository extends JpaRepository<ShopSchedule, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from ShopSchedule schedule where schedule.id = :id")
    Optional<ShopSchedule> lockById(@Param("id") Long id);
}
