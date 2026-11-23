package com.cubetech.eventpass.event;

import com.cubetech.eventpass.audit.AuditAction;
import com.cubetech.eventpass.audit.AuditLog.TargetType;
import com.cubetech.eventpass.audit.AuditService;
import com.cubetech.eventpass.common.ApiException;
import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.EventDtos.EventRequest;
import com.cubetech.eventpass.event.EventDtos.PublicEventResponse;
import com.cubetech.eventpass.event.EventDtos.StaffEventResponse;
import com.cubetech.eventpass.registration.EventCounts;
import com.cubetech.eventpass.registration.RegistrationRepository;
import com.cubetech.eventpass.registration.TicketCodes;
import com.cubetech.eventpass.staff.StaffPrincipal;
import com.cubetech.eventpass.staff.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final AuditService audit;
    private final Clock clock;

    public EventService(EventRepository events, RegistrationRepository registrations, AuditService audit,
                        Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public List<PublicEventResponse> listPublic() {
        Instant now = Instant.now(clock);
        List<Event> upcoming = events.findPublicUpcoming(now);
        Map<Long, EventCounts> counts = EventCounts.load(registrations, upcoming.stream().map(Event::getId).toList());
        return upcoming.stream()
                .map(event -> PublicEventResponse.of(event, counts.getOrDefault(event.getId(), EventCounts.EMPTY), now))
                .toList();
    }

    /** Drafts are invisible to attendees. Closed and ended events stay viewable so existing links work. */
    @Transactional(readOnly = true)
    public PublicEventResponse getPublic(Long id) {
        Event event = events.findById(id)
                .filter(found -> found.getStatus() != EventStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Event not found."));
        return PublicEventResponse.of(event, countsFor(event.getId()), Instant.now(clock));
    }

    // ----------------------------------------------------------------- staff

    /** Organizers see every event; door staff do not see drafts. */
    @Transactional(readOnly = true)
    public List<StaffEventResponse> listForStaff(StaffPrincipal staff) {
        Instant now = Instant.now(clock);
        List<Event> all = staff.role() == StaffRole.ORGANIZER
                ? events.findAllByOrderByStartsAtDescIdDesc()
                : events.findByStatusNotOrderByStartsAtDescIdDesc(EventStatus.DRAFT);
        Map<Long, EventCounts> counts = EventCounts.load(registrations, all.stream().map(Event::getId).toList());
        return all.stream()
                .map(event -> StaffEventResponse.of(event, counts.getOrDefault(event.getId(), EventCounts.EMPTY), now))
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffEventResponse getForStaff(Long id, StaffPrincipal staff) {
        Event event = findVisibleToStaff(id, staff);
        return StaffEventResponse.of(event, countsFor(id), Instant.now(clock));
    }

    /** Loads an event the given staff member may work with (door staff cannot see drafts). */
    @Transactional(readOnly = true)
    public Event findVisibleToStaff(Long id, StaffPrincipal staff) {
        return events.findById(id)
                .filter(event -> staff.role() == StaffRole.ORGANIZER || event.getStatus() != EventStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Event not found."));
    }

    @Transactional
    public StaffEventResponse create(EventRequest request, StaffPrincipal staff) {
        Instant now = Instant.now(clock);
        Schedule schedule = Schedule.of(request);
        if (!schedule.endsAt().isAfter(now)) {
            throw ApiException.fieldError("date", "The event must end in the future.");
        }
        Event event = new Event(request.title().trim(), request.description().trim(), request.venue().trim(),
                schedule.startsAt(), schedule.endsAt(), request.capacity(), request.status(),
                prefixFor(request), staff.id(), now);
        events.save(event);
        audit.record(staff, AuditAction.EVENT_CREATED, TargetType.EVENT, event.getId(),
                "Created \"" + event.getTitle() + "\" (" + event.getStatus() + ", capacity " + event.getCapacity() + ")");
        return StaffEventResponse.of(event, EventCounts.EMPTY, now);
    }

    @Transactional
    public StaffEventResponse update(Long id, EventRequest request, StaffPrincipal staff) {
        Instant now = Instant.now(clock);
        Schedule schedule = Schedule.of(request);
        // Lock the event so a concurrent registration cannot slip in between the capacity check and the save.
        Event event = events.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Event not found."));
        EventCounts counts = countsFor(id);

        if (request.capacity() < counts.registered()) {
            throw ApiException.fieldError("capacity", "Capacity cannot be lower than the " + counts.registered()
                    + " attendee" + (counts.registered() == 1 ? "" : "s") + " already registered.");
        }
        validateStatusChange(event, request.status(), counts);

        List<String> changes = describeChanges(event, request, schedule);
        event.update(request.title().trim(), request.description().trim(), request.venue().trim(),
                schedule.startsAt(), schedule.endsAt(), request.capacity(), request.status(), prefixFor(request), now);
        audit.record(staff, AuditAction.EVENT_UPDATED, TargetType.EVENT, event.getId(),
                changes.isEmpty() ? "No changes" : "Updated " + String.join(", ", changes));
        return StaffEventResponse.of(event, counts, now);
    }

    @Transactional
    public StaffEventResponse changeStatus(Long id, EventStatus status, StaffPrincipal staff) {
        Instant now = Instant.now(clock);
        Event event = events.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Event not found."));
        EventCounts counts = countsFor(id);
        validateStatusChange(event, status, counts);
        EventStatus previous = event.getStatus();
        event.changeStatus(status, now);
        audit.record(staff, AuditAction.EVENT_STATUS_CHANGED, TargetType.EVENT, event.getId(),
                "Status " + previous + " → " + status);
        return StaffEventResponse.of(event, counts, now);
    }

    private void validateStatusChange(Event event, EventStatus target, EventCounts counts) {
        boolean hasRegistrations = counts.registered() + counts.waitlisted() > 0;
        if (target == EventStatus.DRAFT && event.getStatus() != EventStatus.DRAFT && hasRegistrations) {
            throw new ApiException(HttpStatus.CONFLICT, "HAS_REGISTRATIONS",
                    "An event with registrations cannot be moved back to Draft. Close it instead.");
        }
    }

    private EventCounts countsFor(Long eventId) {
        return EventCounts.load(registrations, List.of(eventId)).getOrDefault(eventId, EventCounts.EMPTY);
    }

    private static String prefixFor(EventRequest request) {
        if (request.ticketPrefix() != null && !request.ticketPrefix().isBlank()) {
            return request.ticketPrefix().trim().toUpperCase(Locale.ROOT);
        }
        return TicketCodes.prefixFromTitle(request.title());
    }

    private static List<String> describeChanges(Event event, EventRequest request, Schedule schedule) {
        List<String> changes = new ArrayList<>();
        if (!event.getTitle().equals(request.title().trim())) {
            changes.add("title");
        }
        if (!event.getDescription().equals(request.description().trim())) {
            changes.add("description");
        }
        if (!event.getVenue().equals(request.venue().trim())) {
            changes.add("venue");
        }
        if (!event.getStartsAt().equals(schedule.startsAt()) || !event.getEndsAt().equals(schedule.endsAt())) {
            changes.add("schedule");
        }
        if (event.getCapacity() != request.capacity()) {
            changes.add("capacity " + event.getCapacity() + " → " + request.capacity());
        }
        if (event.getStatus() != request.status()) {
            changes.add("status " + event.getStatus() + " → " + request.status());
        }
        return changes;
    }

    /** Start/end instants derived from a Cebu-local date and times, with the end-after-start rule. */
    record Schedule(Instant startsAt, Instant endsAt) {

        static Schedule of(EventRequest request) {
            if (!request.endTime().isAfter(request.startTime())) {
                throw ApiException.fieldError("endTime", "End time must be after the start time.");
            }
            return new Schedule(
                    request.date().atTime(request.startTime()).atZone(TimeConfig.EVENT_ZONE).toInstant(),
                    request.date().atTime(request.endTime()).atZone(TimeConfig.EVENT_ZONE).toInstant());
        }
    }
}
