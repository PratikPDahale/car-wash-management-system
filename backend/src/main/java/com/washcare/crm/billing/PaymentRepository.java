package com.washcare.crm.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByPaidAtAsc(Long invoiceId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(payment.amount), 0) from Payment payment "
            + "where payment.invoice.id = :invoiceId and payment.status = :status")
    BigDecimal sumAmountByInvoiceIdAndStatus(
            @Param("invoiceId") Long invoiceId,
            @Param("status") PaymentStatus status);
}
