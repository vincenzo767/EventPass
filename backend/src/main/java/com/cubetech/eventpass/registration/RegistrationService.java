package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.common.ApiException;
import com.cubetech.eventpass.config.AppProperties;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventRepository;
import com.cubetech.eventpass.event.EventStatus;
import com.cubetech.eventpass.notification.TicketEmailRequested;
import com.cubetech.eventpass.registration.RegistrationDtos.RegistrationRequest;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketLookupRequest;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Attendee-facing registration operations: register, look up a ticket, cancel. */
@Service
public class RegistrationService {

    static final String TICKET_NOT_FOUND = "Ticket not found.";

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final TicketCodes ticketCodes;
    private final TicketMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final AppProperties properties;
    private final Clock clock;

    public RegistrationService(EventRepository events, RegistrationRepository registrations, TicketCodes ticketCodes,
                               TicketMapper mapper, ApplicationEventPublisher publisher, AppProperties properties,
                               Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.ticketCodes = ticketCodes;
        this.mapper = mapper;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Registers an attendee. The event row is locked ({@code SELECT ... FOR UPDATE}) before seats are
     * counted, so concurrent registrations for the same event run one at a time: when two people race
     * for the last seat, the second one sees the event as full and is waitlisted or refused.
     */
    @Transactional
    public TicketResponse register(Long eventId, RegistrationRequest request) {
        Instant now = Instant.now(clock);
        String fullName = normalizeName(request.fullName());
        String email = normalizeEmail(request.email());
        String mobile = request.mobile().trim();

        Event event = events.findByIdForUpdate(eventId)
                .filter(found -> found.getStatus() != EventStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Event not found."));
        if (event.hasEnded(now)) {
            throw ApiException.conflict("EVENT_ENDED", "This event has already ended.");
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "Registration for this event is closed.");
        }
        if (registrations.existsActiveByEventAndEmail(eventId, email)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION",
                    "This email is already registered for this event. Use \"Find my ticket\" to view your ticket.",
                    java.util.Map.of("email", "This email is already registered for this event."));
        }

        long seatsTaken = registrations.countByEventIdAndStatusIn(eventId, RegistrationStatus.SEAT_HOLDING);
        RegistrationStatus status;
        if (seatsTaken < event.getCapacity()) {
            status = RegistrationStatus.CONFIRMED;
        } else if (request.joinWaitlist()) {
            status = RegistrationStatus.WAITLISTED;
        } else {
            throw ApiException.conflict("EVENT_FULL", "Sorry, this event is now full. You can join the waitlist instead.");
        }

        Registration registration = new Registration(event, fullName, email, mobile, uniqueTicketCode(event),
                ticketCodes.newAccessToken(), status, now);
        registrations.save(registration);

        publisher.publishEvent(new TicketEmailRequested(registration.getId(),
                status == RegistrationStatus.CONFIRMED ? TicketEmailRequested.Kind.CONFIRMED
                        : TicketEmailRequested.Kind.WAITLISTED));
        return mapper.toTicket(registration, properties.mail().enabled());
    }

    /**
     * Finds a ticket by code plus the registered email or mobile number. Any mismatch returns the same
     * "Ticket not found." error, so the response never reveals whether a code exists.
     */
    @Transactional(readOnly = true)
    public TicketResponse lookup(TicketLookupRequest request) {
        String code = TicketCodes.normalize(request.ticketCode());
        if (code == null) {
            throw ticketNotFound();
        }
        Registration registration = registrations.findByTicketCode(code)
                .filter(found -> contactMatches(found, request.contact()))
                .orElseThrow(RegistrationService::ticketNotFound);
        return mapper.toTicket(registration, null);
    }

    @Transactional(readOnly = true)
    public TicketResponse getByAccessToken(String token) {
        return mapper.toTicket(findByToken(token), null);
    }

    /** Lets attendees cancel their own confirmed or waitlisted registration from the ticket page. */
    @Transactional
    public TicketResponse cancelByAttendee(String token) {
        if (token == null || token.length() > 64) {
            throw ticketNotFound();
        }
        Long registrationId = registrations.findIdByAccessToken(token).orElseThrow(RegistrationService::ticketNotFound);
        Long eventId = registrations.findEventIdById(registrationId).orElseThrow();
        // Lock order (event, then registration) matches every other seat-changing operation.
        events.findByIdForUpdate(eventId);
        Registration registration = registrations.findByIdForUpdate(registrationId).orElseThrow();
        RegistrationRules.requireCancellable(registration);
        registration.cancel(null, Instant.now(clock));
        return mapper.toTicket(registration, null);
    }

    /** Returns the registration for a confirmed/checked-in ticket, used to render its QR code. */
    @Transactional(readOnly = true)
    public String qrPayloadForToken(String token) {
        Registration registration = findByToken(token);
        if (!registration.getStatus().holdsSeat()) {
            throw ApiException.notFound("No QR code is available for this ticket.");
        }
        // The QR contains only the ticket code: no name, email or other personal data.
        return registration.getTicketCode();
    }

    private Registration findByToken(String token) {
        if (token == null || token.length() > 64) {
            throw ticketNotFound();
        }
        return registrations.findByAccessToken(token).orElseThrow(RegistrationService::ticketNotFound);
    }

    private String uniqueTicketCode(Event event) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = ticketCodes.newTicketCode(event.getTicketPrefix());
            if (!registrations.existsByTicketCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique ticket code");
    }

    private static boolean contactMatches(Registration registration, String contact) {
        String value = contact.trim();
        if (value.contains("@")) {
            return registration.getEmail().equals(normalizeEmail(value));
        }
        String digits = value.replaceAll("[\\s-]", "");
        return registration.getMobile().equals(digits);
    }

    private static ApiException ticketNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", TICKET_NOT_FOUND);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
