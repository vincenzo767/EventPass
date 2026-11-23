package com.cubetech.eventpass.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthAndAccessTest extends IntegrationTest {

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn();
    }

    @Test
    void validLoginStartsSessionAndPasswordIsStoredHashed() throws Exception {
        MvcResult result = login("Organizer", STAFF_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("role").asText()).isEqualTo("ORGANIZER");
        assertThat(body(result).has("password")).isFalse();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("organizer"));

        String hash = staffUsers.findByUsername("organizer").orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").doesNotContain(STAFF_PASSWORD);
    }

    @Test
    void invalidLoginIsRejectedWithGenericMessage() throws Exception {
        MvcResult wrongPassword = login("organizer", "wrong-password");
        MvcResult unknownUser = login("nobody", STAFF_PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(wrongPassword).get("message").asText()).isEqualTo("Incorrect username or password.");
        assertThat(body(unknownUser).get("message").asText()).isEqualTo("Incorrect username or password.");
    }

    @Test
    void deactivatedStaffCannotSignIn() throws Exception {
        StaffUser door = staffUsers.findByUsername("door").orElseThrow();
        door.setActive(false);
        staffUsers.save(door);
        assertThat(login("door", STAFF_PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unauthenticatedApiAccessIsBlocked() throws Exception {
        Event event = todayEvent("Meetup", 30);
        mvc.perform(get("/api/staff/events")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/staff/events/{id}/registrations", event.getId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/staff/events/{id}/check-ins", event.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketCode\":\"TST-23456-23456\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void openingStaffPageWhileSignedOutRedirectsToSignIn() throws Exception {
        mvc.perform(get("/staff/check-in"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/login?next=%2Fstaff%2Fcheck-in"));
        mvc.perform(get("/staff")).andExpect(status().is3xxRedirection());
    }

    @Test
    void doorStaffCannotManageEventsOrRegistrations() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String eventBody = json.writeValueAsString(Map.of("title", "X", "description", "Y", "venue", "Z",
                "date", NOW.toLocalDate().plusDays(5).toString(), "startTime", "09:00", "endTime", "10:00",
                "capacity", 10, "status", "PUBLISHED"));

        mvc.perform(post("/api/staff/events").with(csrf()).with(user(doorStaff))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/staff/events/{id}", event.getId()).with(csrf()).with(user(doorStaff))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/staff/events/{id}/waitlist/promote", event.getId()).with(csrf()).with(user(doorStaff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/events/{id}/registrations/export", event.getId()).with(user(doorStaff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/audit-logs").with(user(doorStaff))).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/users").with(user(doorStaff))).andExpect(status().isForbidden());

        // ...but organizers can.
        mvc.perform(post("/api/staff/events").with(csrf()).with(user(organizer))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isCreated());
    }

    @Test
    void doorStaffCanCheckInAndViewAttendees() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Ana Reyes", "ana@example.com").get("ticketCode").asText();
        mvc.perform(get("/api/staff/events/{id}/registrations", event.getId()).with(user(doorStaff)))
                .andExpect(status().isOk());
        assertThat(checkIn(event.getId(), code, doorStaff).get("result").asText()).isEqualTo("CHECKED_IN");
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"organizer\",\"password\":\"" + STAFF_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCanCreateStaffAccount() throws Exception {
        mvc.perform(post("/api/staff/users").with(csrf()).with(user(organizer)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "New Door", "username", "door2",
                                "password", "long-enough-password", "role", "DOOR_STAFF"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("door2"));
        assertThat(login("door2", "long-enough-password").getResponse().getStatus()).isEqualTo(200);
    }
}
