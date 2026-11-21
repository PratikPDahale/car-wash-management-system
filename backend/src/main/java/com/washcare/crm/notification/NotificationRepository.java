package com.washcare.crm.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndAvailableAtLessThanEqualOrderByCreatedAtDesc(
            Long userId,
            Instant availableAt,
            Pageable pageable);

    long countByUserIdAndReadAtIsNullAndAvailableAtLessThanEqual(Long userId, Instant availableAt);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Notification notification set notification.readAt = :readAt "
            + "where notification.user.id = :userId and notification.readAt is null "
            + "and notification.availableAt <= :availableAt")
    int markAllRead(
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt,
            @Param("availableAt") Instant availableAt);
}
