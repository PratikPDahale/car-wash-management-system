package com.washcare.crm.booking;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!mysql")
public class ShopScheduleBootstrap {

    @Bean
    ApplicationRunner initializeShopSchedule(ShopScheduleRepository repository) {
        return arguments -> {
            if (!repository.existsById(1L)) {
                String days = Stream.of(
                        DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                        .map(Enum::name)
                        .collect(Collectors.joining(","));
                repository.save(new ShopSchedule(
                        1L,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        30,
                        2,
                        days,
                        0));
            }
        };
    }
}
