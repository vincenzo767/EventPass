package com.cubetech.eventpass.notification;

/** Published inside a transaction; the email is sent only after that transaction commits. */
public record TicketEmailRequested(Long registrationId, Kind kind) {

    public enum Kind {
        /** Registration confirmed: ticket with QR code. */
        CONFIRMED,
        /** Joined the waitlist: position, no ticket. */
        WAITLISTED,
        /** Promoted from the waitlist: ticket with QR code. */
        PROMOTED
    }
}
