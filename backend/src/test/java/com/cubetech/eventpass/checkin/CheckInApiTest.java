package com.cubetech.eventpass.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cubetech.eventpass.audit.AuditAction;
import com.cubetech.eventpass.audit.AuditLog;
import com.cubetech.eventpass.audit.AuditLogRepository;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.registration.Registration;
import com.cubetech.eventpass.registration.RegistrationStatus;
import com.cubetech.eventpass.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CheckInApiTest extends IntegrationTest {

    @Autowired
    private AuditLogRepository auditLogs;

    @Test
    void validTicketIsCheckedInAndSaved() throws Exception {
        Event event = todayEvent("Cebu Dev Meetup 2026", 30);
        String code = register(event.getId(), "Juan dela Cruz", "juan@example.com").get("ticketCode").asText();

        JsonNode result = checkIn(event.getId(), code, doorStaff);

        assertThat(result.get("result").asText()).isEqualTo("CHECKED_IN");
        assertThat(result.get("label").asText()).isEqualTo("Checked in");
        assertThat(result.get("tone").asText()).isEqualTo("SUCCESS");
        assertThat(result.get("attendeeName").asText()).isEqualTo("Juan dela Cruz");
        assertThat(result.get("stats").get("checkedIn").asLong()).isEqualTo(1);

        Registration saved = registrationByCode(code);
        assertThat(saved.getStatus()).isEqualTo(RegistrationStatus.CHECKED_IN);
        assertThat(saved.getCheckedInAt()).isEqualTo(NOW.toInstant());
        assertThat(saved.getCheckedInBy()).isEqualTo(doorStaff.id());
        assertThat(auditLogs.findByTargetTypeAndTargetIdOrderByIdAsc(AuditLog.TargetType.REGISTRATION, saved.getId()))
                .extracting(AuditLog::getAction).containsExactly(AuditAction.CHECK_IN);
    }

    @Test
    void secondCheckInShowsAlreadyCheckedInWithOriginalTime() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Juan dela Cruz", "juan@example.com").get("ticketCode").asText();
        JsonNode first = checkIn(event.getId(), code, doorStaff);

        clock.set(NOW.toInstant().plus(Duration.ofMinutes(25)));
        JsonNode second = checkIn(event.getId(), code, organizer);

        assertThat(second.get("result").asText()).isEqualTo("ALREADY_CHECKED_IN");
        assertThat(second.get("label").asText()).isEqualTo("Already checked in");
        assertThat(second.get("tone").asText()).isEqualTo("WARNING");
        assertThat(Instant.parse(second.get("checkedInAt").asText()))
                .isEqualTo(Instant.parse(first.get("checkedInAt").asText()))
                .isEqualTo(NOW.toInstant());
        assertThat(registrationByCode(code).getCheckedInBy()).isEqualTo(doorStaff.id());
    }

    @Test
    void madeUpCodeIsInvalidAndRevealsNothing() throws Exception {
        Event event = todayEvent("Meetup", 30);
        register(event.getId(), "Juan dela Cruz", "juan@example.com");

        for (String code : new String[] {"CDM-000001", "TST-23456-23456", "hello", "' OR 1=1 --"}) {
            JsonNode result = checkIn(event.getId(), code, doorStaff);
            assertThat(result.get("result").asText()).isEqualTo("INVALID");
            assertThat(result.get("label").asText()).isEqualTo("Invalid ticket");
            assertThat(result.get("tone").asText()).isEqualTo("ERROR");
            assertThat(result.has("attendeeName")).isFalse();
        }
    }

    @Test
    void editedOrSequentialVariantsOfARealCodeAreInvalid() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Juan dela Cruz", "juan@example.com").get("ticketCode").asText();
        char last = code.charAt(code.length() - 1);
        String edited = code.substring(0, code.length() - 1) + (last == 'Z' ? 'Y' : (char) (last + 1));

        assertThat(checkIn(event.getId(), edited, doorStaff).get("result").asText()).isEqualTo("INVALID");
        assertThat(registrationByCode(code).getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void realTicketFromDifferentEventIsInvalid() throws Exception {
        Event selected = todayEvent("Selected event", 30);
        Event other = todayEvent("Other event", 30);
        String otherCode = register(other.getId(), "Juan dela Cruz", "juan@example.com").get("ticketCode").asText();

        JsonNode result = checkIn(selected.getId(), otherCode, doorStaff);

        assertThat(result.get("result").asText()).isEqualTo("INVALID");
        assertThat(result.has("attendeeName")).isFalse();
        assertThat(registrationByCode(otherCode).getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void waitlistedTicketShowsOnWaitlistNotConfirmed() throws Exception {
        Event event = todayEvent("Tiny", 1);
        register(event.getId(), "First", "first@example.com");
        String waitlistCode = register(event.getId(), "Second", "second@example.com").get("ticketCode").asText();

        JsonNode result = checkIn(event.getId(), waitlistCode, doorStaff);

        assertThat(result.get("result").asText()).isEqualTo("WAITLISTED");
        assertThat(result.get("label").asText()).isEqualTo("On waitlist — not confirmed");
        assertThat(result.get("tone").asText()).isEqualTo("ERROR");
        assertThat(registrationByCode(waitlistCode).getStatus()).isEqualTo(RegistrationStatus.WAITLISTED);
    }

    @Test
    void cancelledTicketShowsCancelled() throws Exception {
        Event event = todayEvent("Meetup", 30);
        JsonNode ticket = register(event.getId(), "Juan", "juan@example.com");
        mvc.perform(post("/api/public/tickets/{t}/cancel", ticket.get("accessToken").asText()).with(csrf()))
                .andExpect(status().isOk());

        JsonNode result = checkIn(event.getId(), ticket.get("ticketCode").asText(), doorStaff);
        assertThat(result.get("result").asText()).isEqualTo("CANCELLED");
        assertThat(result.get("label").asText()).isEqualTo("Cancelled");
    }

    @Test
    void checkInIsOnlyAllowedOnTheEventDay() throws Exception {
        Event event = createEvent("Next week", 30, com.cubetech.eventpass.event.EventStatus.PUBLISHED,
                NOW.toLocalDate().plusDays(7), java.time.LocalTime.of(18, 0), java.time.LocalTime.of(21, 0));
        String code = register(event.getId(), "Juan", "juan@example.com").get("ticketCode").asText();

        JsonNode early = checkIn(event.getId(), code, doorStaff);
        assertThat(early.get("result").asText()).isEqualTo("NOT_EVENT_DAY");
        assertThat(registrationByCode(code).getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);

        // Early morning on the event day (before the doors open) is allowed.
        clock.set(NOW.plusDays(7).withHour(7).toInstant());
        assertThat(checkIn(event.getId(), code, doorStaff).get("result").asText()).isEqualTo("CHECKED_IN");
    }

    @Test
    void undoCheckInRestoresConfirmedAndIsRecorded() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Juan", "juan@example.com").get("ticketCode").asText();
        JsonNode checkedIn = checkIn(event.getId(), code, doorStaff);
        long registrationId = checkedIn.get("registrationId").asLong();

        clock.set(NOW.toInstant().plus(Duration.ofMinutes(3)));
        mvc.perform(post("/api/staff/registrations/{id}/undo-check-in", registrationId).with(csrf()).with(user(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.checkedIn").value(0));

        Registration undone = registrationByCode(code);
        assertThat(undone.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(undone.getCheckedInAt()).isNull();
        assertThat(undone.getCheckInUndoneBy()).isEqualTo(organizer.id());
        assertThat(undone.getCheckInUndoneAt()).isEqualTo(NOW.toInstant().plus(Duration.ofMinutes(3)));
        assertThat(auditLogs.findByTargetTypeAndTargetIdOrderByIdAsc(AuditLog.TargetType.REGISTRATION, registrationId))
                .extracting(AuditLog::getAction, AuditLog::getStaffUserId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AuditAction.CHECK_IN, doorStaff.id()),
                        org.assertj.core.groups.Tuple.tuple(AuditAction.CHECK_IN_UNDONE, organizer.id()));

        // The ticket can be checked in again.
        assertThat(checkIn(event.getId(), code, doorStaff).get("result").asText()).isEqualTo("CHECKED_IN");
        // Undoing something that is not checked in is refused.
        mvc.perform(post("/api/staff/registrations/{id}/undo-check-in", registrationId).with(csrf()).with(user(organizer)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/staff/registrations/{id}/undo-check-in", registrationId).with(csrf()).with(user(organizer)))
                .andExpect(status().isConflict());
    }
}
