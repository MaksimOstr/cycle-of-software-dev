package com.cycleofsoftwaredev.shared.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Common infrastructure beans. The clock is injected so that time-based rules can be tested; it ticks in
 * microseconds, the precision of PostgreSQL timestamps, so a saved time is equal to the one read back.
 */
@Configuration
@EnableScheduling
class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
