package com.washcare.crm.search;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'CUSTOMER')")
    public SearchResponse search(@RequestParam(defaultValue = "") String q, Authentication authentication) {
        String query = q.trim();
        if (query.length() < 2) {
            return SearchResponse.empty();
        }
        if (query.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search terms must be 100 characters or fewer.");
        }

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        boolean customer = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"));
        boolean owner = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        return searchService.search(query, user, customer, owner);
    }

    public record SearchResponse(
            List<CustomerResult> customers,
            List<VehicleResult> vehicles,
            List<BookingResult> bookings,
            List<ServiceResult> services) {

        static SearchResponse empty() {
            return new SearchResponse(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record CustomerResult(Long id, String name, String phone, String email) { }

    public record VehicleResult(Long id, String customerName, String registrationNo, String type) { }

    public record BookingResult(
            Long id,
            String customerName,
            String registrationNo,
            String serviceName,
            java.time.LocalDate date,
            java.time.LocalTime startTime,
            String status) { }

    public record ServiceResult(Long id, String name, String description, java.math.BigDecimal price, boolean active) { }
}
