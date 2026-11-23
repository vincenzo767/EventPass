package com.cubetech.eventpass.staff;

public enum StaffRole {
    /** Manages events, registrations, the waitlist and staff accounts; views audit logs. */
    ORGANIZER,
    /** Checks attendees in at the door; can view events and attendee lists. */
    DOOR_STAFF;

    public String authority() {
        return "ROLE_" + name();
    }
}
