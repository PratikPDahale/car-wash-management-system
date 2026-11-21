package com.washcare.crm.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.washcare.crm.service.ServiceOffering;
import com.washcare.crm.service.ServiceOfferingRepository;

@Configuration
@Profile("!mysql")
public class ServiceCatalogData {

    @Bean
    CommandLineRunner seedServiceCatalog(ServiceOfferingRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new ServiceOffering("Essential Wash", "A careful exterior clean and dry.", new BigDecimal("399.00"), 30));
                repository.save(new ServiceOffering("Interior Refresh", "A fresh, tidy cabin from seats to dashboard.", new BigDecimal("799.00"), 45));
                repository.save(new ServiceOffering("Complete Care", "An inside-and-out clean for the whole car.", new BigDecimal("1299.00"), 75));
                repository.save(new ServiceOffering("Premium Detail", "A deeper clean with a polished finish.", new BigDecimal("2199.00"), 120));
            }
        };
    }
}
