package com.washcare.crm.analytics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/analytics")
public class OwnerAnalyticsController {

    private final OwnerAnalyticsService analyticsService;

    public OwnerAnalyticsController(OwnerAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/owner")
    @PreAuthorize("hasRole('OWNER')")
    public OwnerAnalyticsService.AnalyticsResponse getOwnerReport(
            @RequestParam(defaultValue = "30") int days) {
        if (days < 7 || days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The report period must be between 7 and 365 days.");
        }
        return analyticsService.getReport(days);
    }
}
