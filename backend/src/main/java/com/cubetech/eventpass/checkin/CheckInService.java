package com.cubetech.eventpass.checkin;

import com.cubetech.eventpass.audit.AuditAction;
import com.cubetech.eventpass.audit.AuditLog.TargetType;
import com.cubetech.eventpass.audit.AuditService;
import com.cubetech.eventpass.common.ApiException;
import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventService;
import com.cubetech.eventpass.registration.EventCounts;
import com.cubetech.eventpass.registration.Registration;
import com.cubetech.eventpass.registration.RegistrationRepository;
import com.cubetech.eventpass.registration.RegistrationStatus;
import com.cubetech.eventpass.registration.TicketCodes;
import com.cubetech.eventpass.staff.StaffPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Door check-in. Runs the two checks from the assessment in order: (1) does the code belong to the
 * selected event, then (2) what is the ticket's status. Only a Confirmed ticket on the event day is
 * checked in.
 */
@Service
public class CheckInService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);

    private final EventService eventService;
    private final RegistrationRepository registrations;
    private final AuditService audit;
    private final Clock clock;

    public CheckInService(EventService eventService, RegistrationRepository registrations, AuditService audit,
                          Clock clock) {
        this.eventService = eventService;
        this.registrations = registrations;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * The registration row is read with {@code SELECT ... FOR UPDATE}. If two devices submit the same
     * code at the same moment, the second waits for the first transaction, then sees CHECKED_IN and
     * receives "Already checked in" with the original time.
     */
    @Transactional
    public CheckInResponse checkIn(Long eventId, String rawCode, StaffPrincipal staff) {
        Event event = eventService.findVisibleToStaff(eventId, staff);
        String code = TicketCodes.normalize(rawCode);
        Optional<Registration> found = code == null ? Optional.empty() : registrations.findByTicketCodeForUpdate(code);

        // Check 1: unknown codes and tickets for another event are both simply "Invalid ticket".
        if (found.isEmpty() || !found.get().getEvent().getId().equals(eventId)) {
            return CheckInResponse.invalid(stats(event));
        }

        // Check 2: ticket status.
        Registration registration = found.get();
        Instant now = Instant.now(clock);
        return switch (registration.getStatus()) {
            case CHECKED_IN -> CheckInResponse.of(CheckInResult.ALREADY_CHECKED_IN, registration,
                    "First checked in at the time shown.", stats(event));
            case WAITLISTED -> CheckInResponse.of(CheckInResult.WAITLISTED, registration,
                    "This person is on the waitlist and does not have a confirmed seat.", stats(event));
            case CANCELLED -> CheckInResponse.of(CheckInResult.CANCELLED, registration,
                    "This registration was cancelled.", stats(event));
            case CONFIRMED -> {
                LocalDate today = now.atZone(TimeConfig.EVENT_ZONE).toLocalDate();
                if (!event.isEventDay(today)) {
                    String eventDay = DAY.format(event.getStartsAt().atZone(TimeConfig.EVENT_ZONE));
                    yield CheckInResponse.of(CheckInResult.NOT_EVENT_DAY, registration,
                            "Check-in is only allowed on the event day (" + eventDay + ").", stats(event));
                }
                registration.checkIn(staff.id(), now);
                audit.record(staff, AuditAction.CHECK_IN, TargetType.REGISTRATION, registration.getId(),
                        "Checked in " + registration.getTicketCode() + " (" + registration.getFullName() + ")");
                registrations.flush();
                yield CheckInResponse.of(CheckInResult.CHECKED_IN, registration, "Welcome!", stats(event));
            }
        };
    }

    /** Reverts a mistaken check-in; the ticket becomes Confirmed and can be checked in again. */
    @Transactional
    public CheckInResponse undo(Long registrationId, StaffPrincipal staff) {
        Registration registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found."));
        Event event = eventService.findVisibleToStaff(registration.getEvent().getId(), staff);
        if (registration.getStatus() != RegistrationStatus.CHECKED_IN) {
            throw ApiException.conflict("NOT_CHECKED_IN", "This ticket is not checked in.");
        }
        Instant originalTime = registration.getCheckedInAt();
        registration.undoCheckIn(staff.id(), Instant.now(clock));
        audit.record(staff, AuditAction.CHECK_IN_UNDONE, TargetType.REGISTRATION, registration.getId(),
                "Undid check-in of " + registration.getTicketCode() + " (" + registration.getFullName()
                        + "), originally checked in at " + originalTime);
        registrations.flush();
        return new CheckInResponse(null, null, null, "Check-in undone. The ticket can be checked in again.",
                registration.getId(), registration.getFullName(), registration.getTicketCode(), null,
                stats(event));
    }

    private CheckInResponse.Stats stats(Event event) {
        EventCounts counts = EventCounts.load(registrations, List.of(event.getId()))
                .getOrDefault(event.getId(), EventCounts.EMPTY);
        return new CheckInResponse.Stats(counts.checkedIn(), counts.registered(), event.getCapacity());
    }
}
