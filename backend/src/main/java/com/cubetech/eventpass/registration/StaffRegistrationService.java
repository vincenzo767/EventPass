package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.audit.AuditAction;
import com.cubetech.eventpass.audit.AuditLog.TargetType;
import com.cubetech.eventpass.audit.AuditService;
import com.cubetech.eventpass.common.ApiException;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventDtos.EventStats;
import com.cubetech.eventpass.event.EventRepository;
import com.cubetech.eventpass.event.EventService;
import com.cubetech.eventpass.notification.TicketEmailRequested;
import com.cubetech.eventpass.registration.RegistrationDtos.AttendeeListResponse;
import com.cubetech.eventpass.registration.RegistrationDtos.AttendeeResponse;
import com.cubetech.eventpass.registration.RegistrationDtos.PromotionResponse;
import com.cubetech.eventpass.staff.StaffPrincipal;
import com.cubetech.eventpass.staff.StaffUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-facing registration management: attendee list, cancellation, waitlist promotion, export. */
@Service
public class StaffRegistrationService {

    private final EventRepository events;
    private final EventService eventService;
    private final RegistrationRepository registrations;
    private final StaffUserRepository staffUsers;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public StaffRegistrationService(EventRepository events, EventService eventService,
                                    RegistrationRepository registrations, StaffUserRepository staffUsers,
                                    AuditService audit, ApplicationEventPublisher publisher, Clock clock) {
        this.events = events;
        this.eventService = eventService;
        this.registrations = registrations;
        this.staffUsers = staffUsers;
        this.audit = audit;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttendeeListResponse listAttendees(Long eventId, String query, StaffPrincipal staff) {
        Event event = eventService.findVisibleToStaff(eventId, staff);
        String trimmed = query == null ? "" : query.trim();
        List<Registration> rows = trimmed.isEmpty()
                ? registrations.findAllForEvent(eventId)
                : registrations.searchForEvent(eventId, likePattern(trimmed));
        return new AttendeeListResponse(event.getId(), event.getTitle(), totals(event), toAttendees(rows, eventId));
    }

    /** All registrations for an event, for CSV export. */
    @Transactional(readOnly = true)
    public ExportData exportData(Long eventId, StaffPrincipal staff) {
        Event event = eventService.findVisibleToStaff(eventId, staff);
        return new ExportData(event, toAttendees(registrations.findAllForEvent(eventId), eventId));
    }

    @Transactional
    public AttendeeResponse cancel(Long registrationId, StaffPrincipal staff) {
        Long eventId = registrations.findEventIdById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found."));
        events.findByIdForUpdate(eventId);
        Registration registration = registrations.findByIdForUpdate(registrationId).orElseThrow();
        RegistrationRules.requireCancellable(registration);
        RegistrationStatus previous = registration.getStatus();
        registration.cancel(staff.id(), Instant.now(clock));
        audit.record(staff, AuditAction.REGISTRATION_CANCELLED, TargetType.REGISTRATION, registration.getId(),
                "Cancelled " + registration.getTicketCode() + " (" + registration.getFullName() + "), was " + previous);
        return toAttendees(List.of(registration), eventId).getFirst();
    }

    /**
     * Promotes the earliest waitlisted registration. The event row lock serializes promotions with
     * registrations and cancellations, so one free seat can never be handed out twice.
     */
    @Transactional
    public PromotionResponse promoteNext(Long eventId, StaffPrincipal staff) {
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> ApiException.notFound("Event not found."));
        long seatsTaken = registrations.countByEventIdAndStatusIn(eventId, RegistrationStatus.SEAT_HOLDING);
        if (seatsTaken >= event.getCapacity()) {
            throw ApiException.conflict("NO_SPOT_AVAILABLE",
                    "No spot is available. Cancel a registration or increase the capacity first.");
        }
        Registration next = registrations.findFirstWaitlistedForUpdate(eventId)
                .orElseThrow(() -> ApiException.conflict("WAITLIST_EMPTY", "Nobody is on the waitlist."));
        next.promote(Instant.now(clock));
        audit.record(staff, AuditAction.WAITLIST_PROMOTED, TargetType.REGISTRATION, next.getId(),
                "Promoted " + next.getTicketCode() + " (" + next.getFullName() + ") from the waitlist");
        publisher.publishEvent(new TicketEmailRequested(next.getId(), TicketEmailRequested.Kind.PROMOTED));
        registrations.flush();
        return new PromotionResponse(toAttendees(List.of(next), eventId).getFirst(), totals(event));
    }

    private EventStats totals(Event event) {
        EventCounts counts = EventCounts.load(registrations, List.of(event.getId()))
                .getOrDefault(event.getId(), EventCounts.EMPTY);
        return EventStats.of(event, counts, Instant.now(clock));
    }

    private List<AttendeeResponse> toAttendees(List<Registration> rows, Long eventId) {
        Map<Long, String> staffNames = staffNames(rows);
        Map<Long, Integer> waitlistPositions = waitlistPositions(rows, eventId);
        return rows.stream().map(r -> new AttendeeResponse(
                r.getId(), r.getFullName(), r.getEmail(), r.getMobile(), r.getTicketCode(), r.getStatus(),
                r.getRegisteredAt(), r.getCheckedInAt(), staffNames.get(r.getCheckedInBy()),
                r.getCheckInUndoneAt(), staffNames.get(r.getCheckInUndoneBy()), r.getCancelledAt(),
                cancelledByLabel(r, staffNames), waitlistPositions.get(r.getId()))).toList();
    }

    /** Who cancelled: a staff member's name, "Attendee" for self-service cancellation, or null. */
    private static String cancelledByLabel(Registration registration, Map<Long, String> staffNames) {
        if (registration.getCancelledAt() == null) {
            return null;
        }
        return registration.getCancelledBy() == null ? "Attendee" : staffNames.get(registration.getCancelledBy());
    }

    private Map<Long, String> staffNames(List<Registration> rows) {
        List<Long> ids = rows.stream()
                .flatMap(r -> Stream.of(r.getCheckedInBy(), r.getCheckInUndoneBy(), r.getCancelledBy()))
                .filter(Objects::nonNull).distinct().toList();
        // A HashMap (not Map.of) because lookups are made with null ids for unset columns.
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            staffUsers.findAllById(ids).forEach(user -> names.put(user.getId(), user.getName()));
        }
        return names;
    }

    /** FIFO positions computed over the event's whole waitlist (so they stay correct when filtered). */
    private Map<Long, Integer> waitlistPositions(List<Registration> rows, Long eventId) {
        Map<Long, Integer> positions = new HashMap<>();
        if (rows.stream().noneMatch(r -> r.getStatus() == RegistrationStatus.WAITLISTED)) {
            return positions;
        }
        int position = 0;
        for (Registration r : registrations.findAllForEvent(eventId)) {
            if (r.getStatus() == RegistrationStatus.WAITLISTED) {
                positions.put(r.getId(), ++position);
            }
        }
        return positions;
    }

    private static String likePattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public record ExportData(Event event, List<AttendeeResponse> attendees) {
    }
}
