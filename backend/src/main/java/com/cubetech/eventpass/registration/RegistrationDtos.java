package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.event.EventDtos.EventStats;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class RegistrationDtos {

    /** Shared with the frontend validation (frontend/src/lib/validation.ts). */
    public static final String EMAIL_REGEX =
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$";
    public static final String MOBILE_REGEX = "^09\\d{9}$";

    private RegistrationDtos() {
    }

    public record RegistrationRequest(
            @NotBlank(message = "Full name is required.")
            @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters.")
            String fullName,

            @NotBlank(message = "Email address is required.")
            @Size(max = 254, message = "Email address is too long.")
            @Pattern(regexp = EMAIL_REGEX, message = "Enter a valid email address, e.g. juan@example.com.")
            String email,

            @NotBlank(message = "Mobile number is required.")
            @Pattern(regexp = MOBILE_REGEX,
                    message = "Enter an 11-digit mobile number starting with 09, e.g. 09171234567.")
            String mobile,

            /* When the event is full, true places the attendee on the waitlist instead of failing. */
            boolean joinWaitlist) {

        /** Trims input before Bean Validation runs, so stray spaces never cause false errors. */
        public RegistrationRequest {
            fullName = fullName == null ? null : fullName.trim();
            email = email == null ? null : email.trim();
            mobile = mobile == null ? null : mobile.replaceAll("[\\s-]", "");
        }
    }

    public record TicketLookupRequest(
            @NotBlank(message = "Ticket code is required.")
            @Size(max = 40, message = "Ticket not found.")
            String ticketCode,

            @NotBlank(message = "Email or mobile number is required.")
            @Size(max = 254, message = "Ticket not found.")
            String contact) {
    }

    public record TicketEvent(Long id, String title, String venue, Instant startsAt, Instant endsAt) {
    }

    /**
     * An attendee's ticket. Only returned to someone who proved ownership (registration response,
     * code + email/mobile lookup, or the unguessable access token).
     */
    public record TicketResponse(
            String ticketCode,
            String accessToken,
            RegistrationStatus status,
            String attendeeName,
            String maskedEmail,
            Instant registeredAt,
            Instant checkedInAt,
            Instant cancelledAt,
            Integer waitlistPosition,
            boolean cancellable,
            TicketEvent event,
            Boolean emailQueued) {
    }

    public record AttendeeResponse(
            Long id,
            String fullName,
            String email,
            String mobile,
            String ticketCode,
            RegistrationStatus status,
            Instant registeredAt,
            Instant checkedInAt,
            String checkedInBy,
            Instant checkInUndoneAt,
            String checkInUndoneBy,
            Instant cancelledAt,
            String cancelledBy,
            Integer waitlistPosition) {
    }

    /** Attendee list plus totals. Totals always cover the whole event, independent of the search filter. */
    public record AttendeeListResponse(Long eventId, String eventTitle, EventStats totals,
                                       List<AttendeeResponse> attendees) {
    }

    public record PromotionResponse(AttendeeResponse promoted, EventStats totals) {
    }
}
