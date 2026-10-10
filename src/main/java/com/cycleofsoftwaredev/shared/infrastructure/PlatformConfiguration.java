package com.cycleofsoftwaredev.shared.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Common infrastructure beans. The clock is injected so that time-based rules can be tested. */
@Configuration
@EnableScheduling
class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
