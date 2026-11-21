package com.washcare.crm.customer;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @EntityGraph(attributePaths = "customer")
    @Query("""
            select vehicle from Vehicle vehicle
            where lower(vehicle.registrationNo) like lower(concat('%', :query, '%'))
            order by vehicle.registrationNo
            """)
    List<Vehicle> searchForShop(@Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    @Query("""
            select vehicle from Vehicle vehicle
            where vehicle.customer.user.id = :userId
              and lower(vehicle.registrationNo) like lower(concat('%', :query, '%'))
            order by vehicle.registrationNo
            """)
    List<Vehicle> searchForCustomer(
            @Param("query") String query,
            @Param("userId") Long userId,
            Pageable pageable);

    List<Vehicle> findByCustomerIdOrderByRegistrationNoAsc(Long customerId);

    boolean existsByRegistrationNo(String registrationNo);
}
