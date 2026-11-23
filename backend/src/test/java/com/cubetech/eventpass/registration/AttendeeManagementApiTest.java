package com.cubetech.eventpass.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Attendee list, waitlist promotion, staff cancellation, dashboard totals and CSV export. */
class AttendeeManagementApiTest extends IntegrationTest {

    private JsonNode attendeeList(Long eventId, String query) throws Exception {
        var request = get("/api/staff/events/{id}/registrations", eventId).with(user(organizer));
        if (query != null) {
            request.param("q", query);
        }
        return body(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    @Test
    void attendeeListShowsDatabaseTotalsAndSearches() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String anaCode = register(event.getId(), "Ana Reyes", "ana@example.com").get("ticketCode").asText();
        register(event.getId(), "Ben Santos", "ben@example.com");
        register(event.getId(), "Carla Uy", "carla@example.com");
        checkIn(event.getId(), anaCode, doorStaff);

        JsonNode all = attendeeList(event.getId(), null);
        assertThat(all.get("totals").get("registered").asLong()).isEqualTo(3);
        assertThat(all.get("totals").get("capacity").asLong()).isEqualTo(30);
        assertThat(all.get("totals").get("checkedIn").asLong()).isEqualTo(1);
        assertThat(all.get("attendees")).hasSize(3);
        JsonNode ana = all.get("attendees").get(0);
        assertThat(ana.get("fullName").asText()).isEqualTo("Ana Reyes");
        assertThat(ana.get("email").asText()).isEqualTo("ana@example.com");
        assertThat(ana.get("mobile").asText()).isEqualTo("09171234567");
        assertThat(ana.get("status").asText()).isEqualTo("CHECKED_IN");
        assertThat(ana.get("checkedInBy").asText()).isEqualTo("Danny Door");
        assertThat(ana.has("registeredAt")).isTrue();

        JsonNode byName = attendeeList(event.getId(), "santos");
        assertThat(byName.get("attendees")).hasSize(1);
        assertThat(byName.get("attendees").get(0).get("fullName").asText()).isEqualTo("Ben Santos");
        // Totals cover the whole event even when the list is filtered.
        assertThat(byName.get("totals").get("registered").asLong()).isEqualTo(3);

        JsonNode byCode = attendeeList(event.getId(), anaCode.substring(4, 9).toLowerCase());
        assertThat(byCode.get("attendees")).hasSize(1);
        assertThat(attendeeList(event.getId(), "%").get("attendees")).isEmpty();
    }

    @Test
    void waitlistIsFifoAndPromotionFillsFreedSpot() throws Exception {
        Event event = todayEvent("Small", 2);
        JsonNode first = register(event.getId(), "First", "first@example.com");
        register(event.getId(), "Second", "second@example.com");
        clock.set(NOW.toInstant().plus(Duration.ofMinutes(1)));
        JsonNode w1 = register(event.getId(), "Wait One", "w1@example.com");
        clock.set(NOW.toInstant().plus(Duration.ofMinutes(2)));
        JsonNode w2 = register(event.getId(), "Wait Two", "w2@example.com");
        assertThat(w1.get("waitlistPosition").asInt()).isEqualTo(1);
        assertThat(w2.get("waitlistPosition").asInt()).isEqualTo(2);

        // No free seat yet: promotion is refused.
        mvc.perform(post("/api/staff/events/{id}/waitlist/promote", event.getId()).with(csrf()).with(user(organizer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_SPOT_AVAILABLE"));

        // Cancelling a confirmed registration frees one spot.
        long firstId = registrationByCode(first.get("ticketCode").asText()).getId();
        mvc.perform(post("/api/staff/registrations/{id}/cancel", firstId).with(csrf()).with(user(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("Olivia Organizer"));
        mvc.perform(get("/api/public/events/{id}", event.getId())).andExpect(jsonPath("$.spotsLeft").value(1));
        assertThat(checkIn(event.getId(), first.get("ticketCode").asText(), doorStaff).get("label").asText())
                .isEqualTo("Cancelled");

        // Promote: the earliest waitlisted attendee gets the spot.
        mvc.perform(post("/api/staff/events/{id}/waitlist/promote", event.getId()).with(csrf()).with(user(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promoted.fullName").value("Wait One"))
                .andExpect(jsonPath("$.promoted.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totals.spotsLeft").value(0))
                .andExpect(jsonPath("$.totals.waitlisted").value(1));

        // Wait Two moves up to #1; the promoted attendee can now check in.
        mvc.perform(get("/api/public/tickets/{t}", w2.get("accessToken").asText()))
                .andExpect(jsonPath("$.waitlistPosition").value(1));
        assertThat(checkIn(event.getId(), w1.get("ticketCode").asText(), doorStaff).get("result").asText())
                .isEqualTo("CHECKED_IN");
    }

    @Test
    void checkedInRegistrationCannotBeCancelledByStaff() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Ana", "ana@example.com").get("ticketCode").asText();
        long id = checkIn(event.getId(), code, doorStaff).get("registrationId").asLong();

        mvc.perform(post("/api/staff/registrations/{id}/cancel", id).with(csrf()).with(user(organizer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
        assertThat(registrationByCode(code).getStatus()).isEqualTo(RegistrationStatus.CHECKED_IN);
    }

    @Test
    void dashboardCountsMatchAttendeeRecords() throws Exception {
        Event event = todayEvent("Meetup", 3);
        String a = register(event.getId(), "Person A", "a@example.com").get("ticketCode").asText();
        register(event.getId(), "Person B", "b@example.com");
        JsonNode c = register(event.getId(), "Person C", "c@example.com");
        register(event.getId(), "Person D", "d@example.com"); // waitlisted
        checkIn(event.getId(), a, doorStaff);
        mvc.perform(post("/api/public/tickets/{t}/cancel", c.get("accessToken").asText()).with(csrf()));

        JsonNode stats = body(mvc.perform(get("/api/staff/events/{id}", event.getId()).with(user(organizer)))
                .andReturn()).get("stats");
        JsonNode attendees = attendeeList(event.getId(), null).get("attendees");

        assertThat(stats.get("capacity").asLong()).isEqualTo(3);
        assertThat(stats.get("checkedIn").asLong()).isEqualTo(count(attendees, "CHECKED_IN")).isEqualTo(1);
        assertThat(stats.get("confirmed").asLong()).isEqualTo(count(attendees, "CONFIRMED")).isEqualTo(1);
        assertThat(stats.get("waitlisted").asLong()).isEqualTo(count(attendees, "WAITLISTED")).isEqualTo(1);
        assertThat(stats.get("cancelled").asLong()).isEqualTo(count(attendees, "CANCELLED")).isEqualTo(1);
        assertThat(stats.get("registered").asLong()).isEqualTo(2);
        // Event started at 9:00 and "now" is 10:00, so no-shows (confirmed, not checked in) are shown.
        assertThat(stats.get("noShows").asLong()).isEqualTo(1);
    }

    @Test
    void noShowsAreHiddenBeforeTheEventStarts() throws Exception {
        Event event = createEvent("Tonight", 10, com.cubetech.eventpass.event.EventStatus.PUBLISHED,
                NOW.toLocalDate(), java.time.LocalTime.of(18, 0), java.time.LocalTime.of(21, 0));
        register(event.getId(), "Person A", "a@example.com");
        JsonNode stats = body(mvc.perform(get("/api/staff/events/{id}", event.getId()).with(user(organizer)))
                .andReturn()).get("stats");
        assertThat(stats.has("noShows")).isFalse();
    }

    @Test
    void csvExportIsExcelFriendlyAndEscapesFormulas() throws Exception {
        Event event = todayEvent("Cebu Dev Meetup 2026", 30);
        register(event.getId(), "=HYPERLINK(\"http://evil\")", "evil@example.com");
        register(event.getId(), "José Peña, Jr.", "jose@example.com");

        MvcResult result = mvc.perform(get("/api/staff/events/{id}/registrations/export", event.getId())
                        .with(user(organizer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("cebu-dev-meetup-2026-attendees")))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF); // UTF-8 BOM
        String csv = new String(bytes, StandardCharsets.UTF_8).substring(1);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).startsWith("Ticket code,Full name,Email,Mobile,Status");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(csv).contains("\"José Peña, Jr.\"");
        assertThat(csv).contains("0917 123 4567");
    }

    private static long count(JsonNode attendees, String status) {
        long total = 0;
        for (JsonNode attendee : attendees) {
            if (attendee.get("status").asText().equals(status)) {
                total++;
            }
        }
        return total;
    }
}
