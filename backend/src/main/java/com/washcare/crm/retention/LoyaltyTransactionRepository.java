package com.washcare.crm.retention;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    @Query("select coalesce(sum(tx.pointsDelta), 0) from LoyaltyTransaction tx "
            + "where tx.customer.id = :customerId")
    Long balanceForCustomer(@Param("customerId") Long customerId);

    List<LoyaltyTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    boolean existsByInvoiceIdAndTransactionType(Long invoiceId, LoyaltyTransactionType transactionType);

    java.util.Optional<LoyaltyTransaction> findByInvoiceIdAndTransactionType(
            Long invoiceId,
            LoyaltyTransactionType transactionType);
}
