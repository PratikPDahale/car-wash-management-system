package com.washcare.crm.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    List<Customer> findAllByOrderByNameAsc();

    @Query("""
            select customer from Customer customer
            where lower(customer.name) like lower(concat('%', :query, '%'))
               or customer.phone like concat('%', :query, '%')
               or lower(customer.email) like lower(concat('%', :query, '%'))
            order by customer.name
            """)
    List<Customer> search(@Param("query") String query);

    @Query("""
            select customer from Customer customer
            where lower(customer.name) like lower(concat('%', :query, '%'))
               or customer.phone like concat('%', :query, '%')
               or lower(customer.email) like lower(concat('%', :query, '%'))
            order by customer.name
            """)
    List<Customer> searchLimited(@Param("query") String query, Pageable pageable);

    Optional<Customer> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customer from Customer customer where customer.id = :id")
    Optional<Customer> lockById(@Param("id") Long id);
}
