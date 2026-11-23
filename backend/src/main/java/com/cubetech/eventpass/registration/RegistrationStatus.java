package com.cubetech.eventpass.registration;

import java.util.List;

public enum RegistrationStatus {
    CONFIRMED,
    CHECKED_IN,
    WAITLISTED,
    CANCELLED;

    /** Statuses that occupy a seat. Waitlisted and cancelled registrations do not. */
    public static final List<RegistrationStatus> SEAT_HOLDING = List.of(CONFIRMED, CHECKED_IN);

    public boolean holdsSeat() {
        return SEAT_HOLDING.contains(this);
    }
}
