package com.cubetech.eventpass.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.cubetech.eventpass.common.RateLimiter;
import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventRepository;
import com.cubetech.eventpass.event.EventStatus;
import com.cubetech.eventpass.registration.Registration;
import com.cubetech.eventpass.registration.RegistrationRepository;
import com.cubetech.eventpass.staff.StaffPrincipal;
import com.cubetech.eventpass.staff.StaffRole;
import com.cubetech.eventpass.staff.StaffUser;
import com.cubetech.eventpass.staff.StaffUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base class for integration tests: full Spring context, MockMvc, and a real PostgreSQL 17 server
 * (embedded binaries, no Docker needed) with the Flyway migrations applied. Tables are emptied before
 * each test and the clock is reset to a fixed "now".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    /** Fixed test "now": Monday 23 Nov 2026, 10:00 AM in Cebu. */
    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, TimeConfig.EVENT_ZONE);
    public static final String STAFF_PASSWORD = "correct-horse-battery";

    private static final EmbeddedPostgres POSTGRES = startPostgres();
    /** Cost-4 hash keeps tests fast; BCrypt verification reads the cost from the hash itself. */
    private static final String STAFF_PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(STAFF_PASSWORD);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected EventRepository events;
    @Autowired
    protected RegistrationRepository registrations;
    @Autowired
    protected StaffUserRepository staffUsers;
    @Autowired
    protected MutableClock clock;
    @Autowired
    private RateLimiter rateLimiter;

    protected StaffPrincipal organizer;
    protected StaffPrincipal doorStaff;

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW.toInstant(), TimeConfig.EVENT_ZONE);
        }
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE audit_logs, registrations, events, staff_users RESTART IDENTITY CASCADE");
        clock.set(NOW.toInstant());
        rateLimiter.reset();
        organizer = new StaffPrincipal(staffUsers.save(
                new StaffUser("Olivia Organizer", "organizer", STAFF_PASSWORD_HASH, StaffRole.ORGANIZER, now())));
        doorStaff = new StaffPrincipal(staffUsers.save(
                new StaffUser("Danny Door", "door", STAFF_PASSWORD_HASH, StaffRole.DOOR_STAFF, now())));
    }

    // ------------------------------------------------------------ helpers

    protected Instant now() {
        return clock.instant();
    }

    /** Creates an event on {@code date} (Cebu time) directly in the database. */
    protected Event createEvent(String title, int capacity, EventStatus status, LocalDate date, LocalTime start,
                                LocalTime end) {
        Event event = new Event(title, title + " description", "Ayala Center Cebu, Function Room 2",
                date.atTime(start).atZone(TimeConfig.EVENT_ZONE).toInstant(),
                date.atTime(end).atZone(TimeConfig.EVENT_ZONE).toInstant(),
                capacity, status, "TST", organizer.id(), now());
        return events.save(event);
    }

    /** A published event happening today (test "now" falls inside it). */
    protected Event todayEvent(String title, int capacity) {
        return createEvent(title, capacity, EventStatus.PUBLISHED, NOW.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(21, 0));
    }

    protected MvcResult registerRaw(Long eventId, String name, String email, String mobile, boolean joinWaitlist)
            throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", name, "email", email, "mobile", mobile,
                "joinWaitlist", joinWaitlist));
        return mvc.perform(post("/api/public/events/{id}/registrations", eventId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    /** Registers and returns the ticket JSON, failing the test if registration did not succeed. */
    protected JsonNode register(Long eventId, String name, String email) throws Exception {
        MvcResult result = registerRaw(eventId, name, email, "09171234567", true);
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("Registration failed: " + result.getResponse().getContentAsString());
        }
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected Registration registrationByCode(String code) {
        return registrations.findByTicketCode(code).orElseThrow();
    }

    protected JsonNode checkIn(Long eventId, String code, StaffPrincipal staff) throws Exception {
        MvcResult result = mvc.perform(post("/api/staff/events/{id}/check-ins", eventId).with(csrf()).with(user(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ticketCode", code)))).andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Check-in call failed: " + result.getResponse().getContentAsString());
        }
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
