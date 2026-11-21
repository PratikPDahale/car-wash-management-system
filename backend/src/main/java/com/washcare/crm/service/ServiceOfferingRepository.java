package com.washcare.crm.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findByActiveTrueOrderByNameAsc();

    List<ServiceOffering> findAllByOrderByNameAsc();

    @Query("""
            select service from ServiceOffering service
            where lower(service.name) like lower(concat('%', :query, '%'))
              and (:activeOnly = false or service.active = true)
            order by service.name
            """)
    List<ServiceOffering> searchByName(
            @Param("query") String query,
            @Param("activeOnly") boolean activeOnly,
            Pageable pageable);
}
