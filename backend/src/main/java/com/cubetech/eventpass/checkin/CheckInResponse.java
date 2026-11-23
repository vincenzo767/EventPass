package com.cubetech.eventpass.checkin;

import com.cubetech.eventpass.registration.Registration;
import java.time.Instant;

/**
 * Result of a check-in attempt. For {@link CheckInResult#INVALID} no attendee details are included.
 *
 * @param stats live counter values for the event, e.g. "18 / 25 checked in"
 */
public record CheckInResponse(
        CheckInResult result,
        String label,
        CheckInResult.Tone tone,
        String message,
        Long registrationId,
        String attendeeName,
        String ticketCode,
        Instant checkedInAt,
        Stats stats) {

    public record Stats(long checkedIn, long registered, int capacity) {
    }

    static CheckInResponse invalid(Stats stats) {
        return new CheckInResponse(CheckInResult.INVALID, CheckInResult.INVALID.label(), CheckInResult.INVALID.tone(),
                "This code does not match any ticket for this event.", null, null, null, null, stats);
    }

    static CheckInResponse of(CheckInResult result, Registration registration, String message, Stats stats) {
        return new CheckInResponse(result, result.label(), result.tone(), message, registration.getId(),
                registration.getFullName(), registration.getTicketCode(), registration.getCheckedInAt(), stats);
    }
}
