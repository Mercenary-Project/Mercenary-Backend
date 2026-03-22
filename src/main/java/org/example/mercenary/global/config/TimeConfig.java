package org.example.mercenary.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

@Configuration
public class TimeConfig {

    @Value("${app.timezone:Asia/Seoul}")
    private String timezone;

    @PostConstruct
    void setDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
    }

    @Bean
    public Clock appClock() {
        return Clock.system(ZoneId.of(timezone));
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock appClock) {
        return () -> Optional.of(LocalDateTime.now(appClock));
    }
}
