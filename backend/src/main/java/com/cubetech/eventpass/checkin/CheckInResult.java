package com.cubetech.eventpass.checkin;

/** Outcome of validating a ticket at the door, with the exact text shown to staff. */
public enum CheckInResult {
    CHECKED_IN("Checked in", Tone.SUCCESS),
    ALREADY_CHECKED_IN("Already checked in", Tone.WARNING),
    INVALID("Invalid ticket", Tone.ERROR),
    WAITLISTED("On waitlist — not confirmed", Tone.ERROR),
    CANCELLED("Cancelled", Tone.ERROR),
    NOT_EVENT_DAY("Not event day", Tone.ERROR);

    public enum Tone { SUCCESS, WARNING, ERROR }

    private final String label;
    private final Tone tone;

    CheckInResult(String label, Tone tone) {
        this.label = label;
        this.tone = tone;
    }

    public String label() {
        return label;
    }

    public Tone tone() {
        return tone;
    }
}
