package com.washcare.crm.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByBookingId(Long bookingId);

    List<Invoice> findAllByOrderByIssuedAtDesc();

    @Query("select invoice from Invoice invoice where invoice.booking.customer.user.id = :userId "
            + "order by invoice.issuedAt desc")
    List<Invoice> findCustomerInvoices(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from Invoice invoice where invoice.id = :id")
    Optional<Invoice> lockById(@Param("id") Long id);
}
