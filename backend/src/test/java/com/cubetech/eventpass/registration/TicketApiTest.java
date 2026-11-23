package com.cubetech.eventpass.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayInputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class TicketApiTest extends IntegrationTest {

    private MvcResult lookup(String code, String contact) throws Exception {
        return mvc.perform(post("/api/public/tickets/lookup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ticketCode", code, "contact", contact)))).andReturn();
    }

    @Test
    void lookupWithCodeAndEmailReturnsTicket() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Maria Clara", "maria@example.com").get("ticketCode").asText();

        // Lower-case code without dashes and mixed-case email are normalized.
        MvcResult result = lookup(code.toLowerCase().replace("-", ""), "Maria@Example.com");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode ticket = body(result);
        assertThat(ticket.get("ticketCode").asText()).isEqualTo(code);
        assertThat(ticket.get("attendeeName").asText()).isEqualTo("Maria Clara");
        assertThat(ticket.get("maskedEmail").asText()).isEqualTo("m***@example.com");
    }

    @Test
    void lookupWithCodeAndMobileReturnsTicket() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Maria Clara", "maria@example.com").get("ticketCode").asText();
        assertThat(lookup(code, "0917-123-4567").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void wrongEmailOrWrongCodeShowsTicketNotFoundWithoutDetails() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Maria Clara", "maria@example.com").get("ticketCode").asText();
        String editedCode = code.substring(0, code.length() - 1) + (code.endsWith("Z") ? "Y" : "Z");

        for (MvcResult result : new MvcResult[] {
            lookup(code, "someone.else@example.com"),
            lookup(editedCode, "maria@example.com"),
            lookup("CDM-000001", "maria@example.com"),
            lookup(code, "09999999999")}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            String responseBody = result.getResponse().getContentAsString();
            assertThat(body(result).get("message").asText()).isEqualTo("Ticket not found.");
            assertThat(responseBody).doesNotContain("Maria").doesNotContain("maria").doesNotContain(code);
        }
    }

    @Test
    void ticketCanBeReloadedByAccessTokenAfterRefresh() throws Exception {
        Event event = todayEvent("Meetup", 30);
        JsonNode created = register(event.getId(), "Maria Clara", "maria@example.com");

        // A browser refresh re-requests the ticket by its link token; no client-side state is needed.
        mvc.perform(get("/api/public/tickets/{token}", created.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(created.get("ticketCode").asText()))
                .andExpect(jsonPath("$.attendeeName").value("Maria Clara"));
        mvc.perform(get("/api/public/tickets/{token}", "not-a-real-token")).andExpect(status().isNotFound());
    }

    @Test
    void qrCodeContainsExactlyTheTicketCode() throws Exception {
        Event event = todayEvent("Meetup", 30);
        JsonNode created = register(event.getId(), "Maria Clara", "maria@example.com");

        byte[] png = mvc.perform(get("/api/public/tickets/{token}/qr.png", created.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        var image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        assertThat(decoded).isEqualTo(created.get("ticketCode").asText());
    }

    @Test
    void waitlistedRegistrationHasNoQrCode() throws Exception {
        Event event = todayEvent("Tiny", 1);
        register(event.getId(), "First", "first@example.com");
        JsonNode waitlisted = register(event.getId(), "Second", "second@example.com");

        assertThat(waitlisted.get("status").asText()).isEqualTo("WAITLISTED");
        mvc.perform(get("/api/public/tickets/{token}/qr.png", waitlisted.get("accessToken").asText()))
                .andExpect(status().isNotFound());
    }

    @Test
    void attendeeCanCancelAndThenRegisterAgain() throws Exception {
        Event event = todayEvent("Meetup", 2);
        JsonNode created = register(event.getId(), "Maria Clara", "maria@example.com");

        mvc.perform(post("/api/public/tickets/{token}/cancel", created.get("accessToken").asText()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/public/events/{id}", event.getId())).andExpect(jsonPath("$.spotsLeft").value(2));

        JsonNode again = register(event.getId(), "Maria Clara", "maria@example.com");
        assertThat(again.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(again.get("ticketCode").asText()).isNotEqualTo(created.get("ticketCode").asText());
    }

    @Test
    void checkedInRegistrationCannotBeCancelledByAttendee() throws Exception {
        Event event = todayEvent("Meetup", 30);
        JsonNode created = register(event.getId(), "Maria Clara", "maria@example.com");
        checkIn(event.getId(), created.get("ticketCode").asText(), doorStaff);

        mvc.perform(post("/api/public/tickets/{token}/cancel", created.get("accessToken").asText()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
    }

    @Test
    void ticketLookupIsRateLimited() throws Exception {
        // Test profile allows 10 lookups per minute per client.
        for (int i = 0; i < 10; i++) {
            assertThat(lookup("CDM-23456-23456", "x@example.com").getResponse().getStatus()).isEqualTo(404);
        }
        MvcResult limited = lookup("CDM-23456-23456", "x@example.com");
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getHeader("Retry-After")).isNotNull();
    }
}
