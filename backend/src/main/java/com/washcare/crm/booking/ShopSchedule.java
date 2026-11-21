package com.washcare.crm.booking;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shop_schedule")
public class ShopSchedule {

    @Id
    private Long id;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "slot_interval_minutes", nullable = false)
    private int slotIntervalMinutes;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "open_days", nullable = false, length = 100)
    private String openDays;

    @Column(name = "cancellation_cutoff_minutes", nullable = false)
    private int cancellationCutoffMinutes;

    protected ShopSchedule() {
    }

    public ShopSchedule(
            Long id,
            LocalTime openingTime,
            LocalTime closingTime,
            int slotIntervalMinutes,
            int capacity,
            String openDays,
            int cancellationCutoffMinutes) {
        this.id = id;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.slotIntervalMinutes = slotIntervalMinutes;
        this.capacity = capacity;
        this.openDays = openDays;
        this.cancellationCutoffMinutes = cancellationCutoffMinutes;
    }

    public boolean isOpenOn(DayOfWeek day) {
        return getOpenDayValues().contains(day);
    }

    public Set<DayOfWeek> getOpenDayValues() {
        return Set.of(openDays.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public int getSlotIntervalMinutes() {
        return slotIntervalMinutes;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCancellationCutoffMinutes() {
        return cancellationCutoffMinutes;
    }
}
