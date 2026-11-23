package com.cubetech.eventpass.event;

import com.cubetech.eventpass.registration.EventCounts;
import java.time.Instant;

/** What an attendee can do with an event right now. */
public enum Availability {
    OPEN,
    FULL,
    CLOSED,
    ENDED;

    public static Availability of(Event event, EventCounts counts, Instant now) {
        if (event.hasEnded(now)) {
            return ENDED;
        }
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return CLOSED;
        }
        return counts.spotsLeft(event.getCapacity()) == 0 ? FULL : OPEN;
    }
}
