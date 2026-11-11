package com.lazzariniingenieria.clubmanagementapi.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClubClockConfig {

    private static final ZoneId CLUB_TIME_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Clock clubClock() {
        return Clock.system(CLUB_TIME_ZONE);
    }
}
