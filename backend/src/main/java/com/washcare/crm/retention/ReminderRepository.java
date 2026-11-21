package com.washcare.crm.retention;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByCustomerIdOrderByScheduledAtDesc(Long customerId);
}
