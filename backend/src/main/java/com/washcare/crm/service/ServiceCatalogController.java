package com.washcare.crm.service;

import java.util.List;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {

    private final ServiceOfferingRepository serviceOfferingRepository;

    public ServiceCatalogController(ServiceOfferingRepository serviceOfferingRepository) {
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @GetMapping
    public List<ServiceOfferingResponse> getActiveServices() {
        return serviceOfferingRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(ServiceOfferingResponse::from)
                .toList();
    }

    @GetMapping("/manage")
    @PreAuthorize("hasRole('OWNER')")
    public List<ServiceOfferingResponse> getAllServices() {
        return serviceOfferingRepository.findAllByOrderByNameAsc().stream()
                .map(ServiceOfferingResponse::from)
                .toList();
    }

    @GetMapping("/manage/{serviceId}")
    @PreAuthorize("hasRole('OWNER')")
    public ServiceOfferingResponse getService(@PathVariable Long serviceId) {
        ServiceOffering service = serviceOfferingRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
        return ServiceOfferingResponse.from(service);
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ServiceOfferingResponse createService(@Valid @RequestBody ServiceOfferingRequest request) {
        ServiceOffering service = new ServiceOffering(
                request.name().trim(),
                request.description(),
                request.price(),
                request.estimatedDurationMinutes());
        if (request.active() != null && !request.active()) {
            service.update(request.name(), request.description(), request.price(),
                    request.estimatedDurationMinutes(), false);
        }
        return ServiceOfferingResponse.from(serviceOfferingRepository.save(service));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasRole('OWNER')")
    public ServiceOfferingResponse updateService(
            @PathVariable Long serviceId,
            @Valid @RequestBody ServiceOfferingRequest request) {
        ServiceOffering service = serviceOfferingRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
        service.update(
                request.name(),
                request.description(),
                request.price(),
                request.estimatedDurationMinutes(),
                request.active() == null || request.active());
        return ServiceOfferingResponse.from(serviceOfferingRepository.save(service));
    }

    public record ServiceOfferingResponse(
            Long id,
            String name,
            String description,
            BigDecimal price,
            int estimatedDurationMinutes,
            boolean active) {

        private static ServiceOfferingResponse from(ServiceOffering service) {
            return new ServiceOfferingResponse(
                    service.getId(),
                    service.getName(),
                    service.getDescription(),
                    service.getPrice(),
                    service.getEstimatedDurationMinutes(),
                    service.isActive());
        }
    }

    public record ServiceOfferingRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            @Positive int estimatedDurationMinutes,
            Boolean active) {
    }
}
