package com.cubetech.eventpass.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicBaseUrl, Mail mail, Demo demo, RateLimit rateLimit) {

    public record Mail(boolean enabled, String from) {
    }

    public record Demo(boolean seed, boolean rollingDates, String organizerPassword, String doorStaffPassword) {
    }

    public record RateLimit(int ticketLookup, int registration, int checkIn, int login) {
    }
}
