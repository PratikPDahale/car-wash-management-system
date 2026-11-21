package com.washcare.crm.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerAnalyticsService {

    private static final ZoneId SHOP_ZONE = ZoneId.systemDefault();
    private final AnalyticsRepository repository;

    public OwnerAnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getReport(int days) {
        LocalDate endDate = LocalDate.now(SHOP_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1L);
        Instant start = startDate.atStartOfDay(SHOP_ZONE).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(SHOP_ZONE).toInstant();
        Instant todayStart = endDate.atStartOfDay(SHOP_ZONE).toInstant();
        long uniqueCustomers = repository.uniqueCompletedCustomers(startDate, endDate);
        long repeatCustomers = repository.repeatCustomerIds(startDate, endDate).size();
        Double averageRating = repository.averageReviewRating(start, end);

        Map<LocalDate, BigDecimal> dailyAmounts = new HashMap<>();
        for (Object[] row : repository.dailyRevenue(start, end)) {
            dailyAmounts.put(toLocalDate(row[0]), (BigDecimal) row[1]);
        }
        List<RevenuePoint> revenue = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            revenue.add(new RevenuePoint(date, dailyAmounts.getOrDefault(date, BigDecimal.ZERO)));
        }
        List<ServicePopularity> popularServices = repository.popularServices(startDate, endDate).stream()
                .limit(5)
                .map(row -> new ServicePopularity((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new AnalyticsResponse(
                days,
                startDate,
                endDate,
                repository.paidRevenue(start, end),
                repository.paidRevenue(todayStart, end),
                repository.totalBookings(startDate, endDate),
                repository.bookingsOnDate(endDate),
                repository.customerProfiles(),
                repository.completedBookings(startDate, endDate),
                uniqueCustomers,
                repeatCustomers,
                uniqueCustomers == 0 ? 0.0 : repeatCustomers * 100.0 / uniqueCustomers,
                repository.reviewCount(start, end),
                averageRating == null ? 0.0 : averageRating,
                revenue,
                popularServices);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(SHOP_ZONE).toLocalDate();
        throw new IllegalStateException("Unsupported revenue date value: " + value);
    }

    public record AnalyticsResponse(
            int periodDays,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal paidRevenue,
            BigDecimal todayPaidRevenue,
            long bookingCount,
            long todayBookings,
            long customerProfiles,
            long completedServices,
            long uniqueCustomers,
            long repeatCustomers,
            double repeatCustomerRate,
            long reviewCount,
            double averageRating,
            List<RevenuePoint> dailyRevenue,
            List<ServicePopularity> popularServices) {
    }

    public record RevenuePoint(LocalDate date, BigDecimal revenue) {
    }

    public record ServicePopularity(String serviceName, long completedCount) {
    }
}
