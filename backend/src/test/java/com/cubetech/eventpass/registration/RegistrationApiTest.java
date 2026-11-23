package com.cubetech.eventpass.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventStatus;
import com.cubetech.eventpass.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RegistrationApiTest extends IntegrationTest {

    @Test
    void validRegistrationIsSavedWithUniqueTicketCode() throws Exception {
        Event event = todayEvent("Cebu Dev Meetup 2026", 30);

        MvcResult result = registerRaw(event.getId(), "  Juan   dela Cruz ", "Juan.DelaCruz@Example.com",
                "09171234567", false);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode ticket = body(result);
        assertThat(ticket.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(ticket.get("ticketCode").asText()).matches("^TST-[2-9A-HJ-NP-Z]{5}-[2-9A-HJ-NP-Z]{5}$");
        assertThat(ticket.get("attendeeName").asText()).isEqualTo("Juan dela Cruz");
        assertThat(ticket.get("event").get("title").asText()).isEqualTo("Cebu Dev Meetup 2026");
        assertThat(ticket.get("accessToken").asText()).hasSizeGreaterThanOrEqualTo(43);

        Registration saved = registrationByCode(ticket.get("ticketCode").asText());
        assertThat(saved.getEmail()).isEqualTo("juan.delacruz@example.com");
        assertThat(saved.getMobile()).isEqualTo("09171234567");
        assertThat(saved.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void missingFieldsReturnFieldErrors() throws Exception {
        Event event = todayEvent("Meetup", 30);
        mvc.perform(post("/api/public/events/{id}/registrations", event.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.mobile").exists());
        assertThat(registrations.count()).isZero();
    }

    @Test
    void invalidEmailAndTenDigitMobileBothShowErrors() throws Exception {
        Event event = todayEvent("Meetup", 30);
        MvcResult result = registerRaw(event.getId(), "Ana Reyes", "ana@invalid", "0917123456", false);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode errors = body(result).get("fieldErrors");
        assertThat(errors.get("email").asText()).contains("valid email");
        assertThat(errors.get("mobile").asText()).contains("11-digit");
        assertThat(registrations.count()).isZero();
    }

    @Test
    void mobileMustStartWith09() throws Exception {
        Event event = todayEvent("Meetup", 30);
        MvcResult result = registerRaw(event.getId(), "Ana Reyes", "ana@example.com", "08171234567", false);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("fieldErrors").has("mobile")).isTrue();
    }

    @Test
    void duplicateActiveRegistrationIsBlockedCaseInsensitively() throws Exception {
        Event event = todayEvent("Meetup", 30);
        register(event.getId(), "Ana Reyes", "ana@example.com");

        MvcResult second = registerRaw(event.getId(), "Ana R.", "  ANA@Example.COM ", "09181234567", false);

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(second).get("code").asText()).isEqualTo("DUPLICATE_REGISTRATION");
        assertThat(registrations.count()).isEqualTo(1);
    }

    @Test
    void sameEmailMayRegisterForDifferentEvents() throws Exception {
        Event first = todayEvent("Meetup A", 30);
        Event second = todayEvent("Meetup B", 30);
        register(first.getId(), "Ana Reyes", "ana@example.com");
        register(second.getId(), "Ana Reyes", "ana@example.com");
        assertThat(registrations.count()).isEqualTo(2);
    }

    @Test
    void fullEventRefusesRegistrationUnlessJoiningWaitlist() throws Exception {
        Event event = todayEvent("Tiny event", 1);
        register(event.getId(), "First Person", "first@example.com");

        MvcResult refused = registerRaw(event.getId(), "Second Person", "second@example.com", "09171234567", false);
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(refused).get("code").asText()).isEqualTo("EVENT_FULL");

        MvcResult waitlisted = registerRaw(event.getId(), "Second Person", "second@example.com", "09171234567", true);
        assertThat(waitlisted.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(waitlisted).get("status").asText()).isEqualTo("WAITLISTED");
        assertThat(body(waitlisted).get("waitlistPosition").asInt()).isEqualTo(1);
        assertThat(registrations.countByEventIdAndStatusIn(event.getId(), RegistrationStatus.SEAT_HOLDING)).isEqualTo(1);
    }

    @Test
    void publicEventShowsSpotsLeftAndFullStatusFromDatabase() throws Exception {
        Event event = todayEvent("Two seats", 2);
        mvc.perform(get("/api/public/events/{id}", event.getId()))
                .andExpect(jsonPath("$.spotsLeft").value(2))
                .andExpect(jsonPath("$.availability").value("OPEN"));

        register(event.getId(), "One", "one@example.com");
        register(event.getId(), "Two", "two@example.com");

        mvc.perform(get("/api/public/events/{id}", event.getId()))
                .andExpect(jsonPath("$.spotsLeft").value(0))
                .andExpect(jsonPath("$.availability").value("FULL"))
                .andExpect(jsonPath("$.registrationOpen").value(false))
                .andExpect(jsonPath("$.waitlistOpen").value(true));
    }

    @Test
    void draftEventsAreInvisibleAndRejectRegistration() throws Exception {
        Event draft = createEvent("Secret draft", 10, EventStatus.DRAFT, NOW.toLocalDate().plusDays(3),
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        mvc.perform(get("/api/public/events")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/public/events/{id}", draft.getId())).andExpect(status().isNotFound());
        assertThat(registerRaw(draft.getId(), "Ana", "ana@example.com", "09171234567", false)
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void closedAndEndedEventsRejectRegistration() throws Exception {
        Event closed = createEvent("Closed", 10, EventStatus.CLOSED, NOW.toLocalDate().plusDays(3),
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        Event ended = createEvent("Ended", 10, EventStatus.PUBLISHED, NOW.toLocalDate().minusDays(1),
                LocalTime.of(9, 0), LocalTime.of(12, 0));

        assertThat(body(registerRaw(closed.getId(), "Ana", "ana@example.com", "09171234567", true))
                .get("code").asText()).isEqualTo("REGISTRATION_CLOSED");
        assertThat(body(registerRaw(ended.getId(), "Ana", "ana@example.com", "09171234567", true))
                .get("code").asText()).isEqualTo("EVENT_ENDED");
        // Past events are hidden from the public list.
        mvc.perform(get("/api/public/events")).andExpect(jsonPath("$[?(@.title == 'Ended')]").isEmpty());
    }

    @Test
    void publicListReturnsUpcomingPublishedEventsSoonestFirst() throws Exception {
        createEvent("Later", 10, EventStatus.PUBLISHED, NOW.toLocalDate().plusDays(10), LocalTime.of(9, 0),
                LocalTime.of(12, 0));
        todayEvent("Today", 10);
        mvc.perform(get("/api/public/events"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Today"))
                .andExpect(jsonPath("$[0].happeningNow").value(true))
                .andExpect(jsonPath("$[1].title").value("Later"));
    }

    @Test
    void registrationWithoutCsrfTokenIsRejected() throws Exception {
        Event event = todayEvent("Meetup", 30);
        mvc.perform(post("/api/public/events/{id}/registrations", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\",\"email\":\"ana@example.com\",\"mobile\":\"09171234567\"}"))
                .andExpect(status().isForbidden());
    }
}
