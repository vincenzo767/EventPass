package com.cubetech.eventpass.event;

public enum EventStatus {
    /** Being prepared; hidden from attendees. */
    DRAFT,
    /** Visible to attendees and accepting registrations (until full or ended). */
    PUBLISHED,
    /** No longer accepting registrations; existing tickets remain valid for check-in. */
    CLOSED
}
