package com.cubetech.eventpass.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cubetech.eventpass.audit.AuditAction;
import com.cubetech.eventpass.audit.AuditLog;
import com.cubetech.eventpass.audit.AuditLogRepository;
import com.cubetech.eventpass.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EventManagementApiTest extends IntegrationTest {

    @Autowired
    private AuditLogRepository auditLogs;

    private Map<String, Object> eventBody(String status, int capacity, String start, String end) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Cebu Startup Night");
        body.put("description", "Pitches and networking.");
        body.put("venue", "Cebu IT Park");
        body.put("date", NOW.toLocalDate().plusDays(5).toString());
        body.put("startTime", start);
        body.put("endTime", end);
        body.put("capacity", capacity);
        body.put("status", status);
        return body;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/staff/events").with(csrf()).with(user(organizer))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions update(long id, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/staff/events/{id}", id).with(csrf()).with(user(organizer))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void draftIsHiddenUntilPublished() throws Exception {
        JsonNode created = body(create(eventBody("DRAFT", 50, "18:00", "21:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketPrefix").value("CSN"))
                .andReturn());
        long id = created.get("id").asLong();

        mvc.perform(get("/api/public/events")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/public/events/{id}", id)).andExpect(status().isNotFound());

        update(id, eventBody("PUBLISHED", 50, "18:00", "21:00")).andExpect(status().isOk());

        mvc.perform(get("/api/public/events"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Cebu Startup Night"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-28T10:00:00Z")); // 6:00 PM Cebu time
        assertThat(auditLogs.findByTargetTypeAndTargetIdOrderByIdAsc(AuditLog.TargetType.EVENT, id))
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.EVENT_CREATED, AuditAction.EVENT_UPDATED);
    }

    @Test
    void endTimeMustBeAfterStartTime() throws Exception {
        create(eventBody("PUBLISHED", 50, "18:00", "17:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endTime").value("End time must be after the start time."));
        create(eventBody("PUBLISHED", 50, "18:00", "18:00")).andExpect(status().isBadRequest());
        assertThat(events.count()).isZero();
    }

    @Test
    void capacityMustBeAtLeastOneAndFieldsAreRequired() throws Exception {
        create(eventBody("PUBLISHED", 0, "18:00", "21:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1."));
        mvc.perform(post("/api/staff/events").with(csrf()).with(user(organizer))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
    }

    @Test
    void capacityCannotBeLoweredBelowRegisteredAttendees() throws Exception {
        long id = body(create(eventBody("PUBLISHED", 5, "18:00", "21:00")).andReturn()).get("id").asLong();
        register(id, "Person A", "a@example.com");
        register(id, "Person B", "b@example.com");
        register(id, "Person C", "c@example.com");

        update(id, eventBody("PUBLISHED", 2, "18:00", "21:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.capacity")
                        .value("Capacity cannot be lower than the 3 attendees already registered."));
        assertThat(events.findById(id).orElseThrow().getCapacity()).isEqualTo(5);

        update(id, eventBody("PUBLISHED", 3, "18:00", "21:00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.spotsLeft").value(0));
    }

    @Test
    void closedEventStopsRegistrationsAndCannotReturnToDraft() throws Exception {
        long id = body(create(eventBody("PUBLISHED", 5, "18:00", "21:00")).andReturn()).get("id").asLong();
        register(id, "Person A", "a@example.com");

        mvc.perform(post("/api/staff/events/{id}/status", id).with(csrf()).with(user(organizer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("CLOSED"));

        assertThat(body(registerRaw(id, "Person B", "b@example.com", "09171234567", true)).get("code").asText())
                .isEqualTo("REGISTRATION_CLOSED");
        mvc.perform(get("/api/public/events")).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/staff/events/{id}/status", id).with(csrf()).with(user(organizer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void doorStaffDoNotSeeDrafts() throws Exception {
        create(eventBody("DRAFT", 5, "18:00", "21:00"));
        create(eventBody("PUBLISHED", 5, "18:00", "21:00"));
        mvc.perform(get("/api/staff/events").with(user(doorStaff))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/staff/events").with(user(organizer))).andExpect(jsonPath("$.length()").value(2));
    }
}
