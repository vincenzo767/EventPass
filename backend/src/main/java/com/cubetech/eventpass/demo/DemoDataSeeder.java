package com.cubetech.eventpass.demo;

import com.cubetech.eventpass.config.AppProperties;
import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventRepository;
import com.cubetech.eventpass.registration.Registration;
import com.cubetech.eventpass.registration.RegistrationRepository;
import com.cubetech.eventpass.registration.RegistrationStatus;
import com.cubetech.eventpass.registration.TicketCodes;
import com.cubetech.eventpass.staff.StaffRole;
import com.cubetech.eventpass.staff.StaffUser;
import com.cubetech.eventpass.staff.StaffUserRepository;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads demonstration data on startup: two staff accounts (when no staff exist) and the demo events
 * with synthetic attendees (when no events exist). Existing data is never modified or deleted.
 * Disable with {@code DEMO_SEED=false}.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.demo", name = "seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final StaffUserRepository staffUsers;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PasswordEncoder passwordEncoder;
    private final TicketCodes ticketCodes;
    private final AppProperties properties;
    private final Clock clock;

    public DemoDataSeeder(StaffUserRepository staffUsers, EventRepository events, RegistrationRepository registrations,
                          PasswordEncoder passwordEncoder, TicketCodes ticketCodes, AppProperties properties,
                          Clock clock) {
        this.staffUsers = staffUsers;
        this.events = events;
        this.registrations = registrations;
        this.passwordEncoder = passwordEncoder;
        this.ticketCodes = ticketCodes;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock);
        if (staffUsers.count() == 0) {
            staffUsers.save(new StaffUser("Maria Santos", "organizer",
                    passwordEncoder.encode(properties.demo().organizerPassword()), StaffRole.ORGANIZER, now));
            staffUsers.save(new StaffUser("Paolo Reyes", "door",
                    passwordEncoder.encode(properties.demo().doorStaffPassword()), StaffRole.DOOR_STAFF, now));
            log.info("Seeded demo staff accounts 'organizer' (Organizer) and 'door' (Door staff)");
        }
        if (events.count() == 0) {
            StaffUser organizer = staffUsers.findByUsername("organizer").orElseGet(() -> staffUsers.findAll().getFirst());
            StaffUser door = staffUsers.findByUsername("door").orElse(organizer);
            int person = 0;
            for (DemoEvents.Definition definition : DemoEvents.ALL) {
                person = seedEvent(definition, organizer, door, person, now);
            }
            log.info("Seeded {} demo events", DemoEvents.ALL.size());
        }
    }

    private int seedEvent(DemoEvents.Definition d, StaffUser organizer, StaffUser door, int person, Instant now) {
        LocalDate date = LocalDate.now(clock.withZone(TimeConfig.EVENT_ZONE)).plusDays(d.dayOffset());
        Instant startsAt = date.atTime(d.start()).atZone(TimeConfig.EVENT_ZONE).toInstant();
        Instant endsAt = date.atTime(d.end()).atZone(TimeConfig.EVENT_ZONE).toInstant();
        Instant createdAt = d.dayOffset() < 0 ? startsAt.minus(Duration.ofDays(30)) : now.minus(Duration.ofDays(21));
        Event event = new Event(d.title(), d.description(), d.venue(), startsAt, endsAt, d.capacity(), d.status(),
                d.prefix(), organizer.getId(), createdAt);
        event.markAsDemo(d.key());
        events.save(event);

        int total = d.confirmed() + d.waitlisted();
        for (int i = 0; i < total; i++, person++) {
            String first = DemoEvents.FIRST_NAMES.get(person % DemoEvents.FIRST_NAMES.size());
            String last = DemoEvents.LAST_NAMES.get((person * 7) % DemoEvents.LAST_NAMES.size());
            boolean waitlisted = i >= d.confirmed();
            // Registrations spread over the days before "now" (or before a past event), oldest first.
            Instant registeredAt = createdAt.plus(Duration.ofHours(6L + i * 5L));
            Registration registration = new Registration(event, first + " " + last, email(first, last, person),
                    mobile(person), ticketCodes.newTicketCode(d.prefix()), ticketCodes.newAccessToken(),
                    waitlisted ? RegistrationStatus.WAITLISTED : RegistrationStatus.CONFIRMED, registeredAt);
            if (i < d.checkedIn()) {
                Instant checkInTime = d.dayOffset() < 0
                        ? startsAt.plus(Duration.ofMinutes(5L + i * 3L))
                        : now.minus(Duration.ofMinutes(40L - i * 7L));
                registration.checkIn(i % 2 == 0 ? door.getId() : organizer.getId(), checkInTime);
            }
            registrations.save(registration);
        }
        return person;
    }

    /** Synthetic addresses on the reserved example.com domain. */
    private static String email(String first, String last, int person) {
        String base = Normalizer.normalize(first + "." + last, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z.]", "").toLowerCase(Locale.ROOT);
        return base + (person + 1) + "@example.com";
    }

    private static String mobile(int person) {
        String[] networks = {"0917", "0918", "0927", "0998", "0905", "0966"};
        return networks[person % networks.length] + String.format("%07d", 1_000_000 + person * 7_919 % 8_999_999);
    }
}
