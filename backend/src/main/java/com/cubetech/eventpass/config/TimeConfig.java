package com.cubetech.eventpass.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** All business dates (event day, display formatting) are evaluated in Cebu time. */
    public static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Manila");

    /** Injected wherever "now" is needed so time-dependent rules can be tested deterministically. */
    @Bean
    public Clock clock() {
        return Clock.system(EVENT_ZONE);
    }
}
