package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.registration.RegistrationDtos.AttendeeResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes attendee lists as CSV that opens cleanly in Microsoft Excel:
 * <ul>
 *   <li>UTF-8 with a byte-order mark, so names with accents (e.g. "Peña") display correctly;</li>
 *   <li>CRLF line endings and RFC 4180 quoting;</li>
 *   <li>formula-injection protection: cells starting with {@code = + - @}, tab or carriage return are
 *       prefixed with an apostrophe so Excel treats them as text, never as formulas;</li>
 *   <li>mobile numbers written with spaces ("0917 123 4567") so Excel keeps the leading zero.</li>
 * </ul>
 */
public final class AttendeeCsvWriter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd hh:mm a", Locale.ENGLISH).withZone(TimeConfig.EVENT_ZONE);
    private static final String[] HEADER = {
        "Ticket code", "Full name", "Email", "Mobile", "Status", "Waitlist position",
        "Registered at (Asia/Manila)", "Checked in at (Asia/Manila)", "Checked in by", "Cancelled at (Asia/Manila)",
        "Cancelled by"
    };

    private AttendeeCsvWriter() {
    }

    public static byte[] write(List<AttendeeResponse> attendees) {
        StringBuilder csv = new StringBuilder("﻿");
        appendRow(csv, HEADER);
        for (AttendeeResponse a : attendees) {
            appendRow(csv, new String[] {
                a.ticketCode(), a.fullName(), a.email(), formatMobile(a.mobile()), label(a.status()),
                a.waitlistPosition() == null ? "" : a.waitlistPosition().toString(),
                format(a.registeredAt()), format(a.checkedInAt()), nullToEmpty(a.checkedInBy()),
                format(a.cancelledAt()), nullToEmpty(a.cancelledBy())
            });
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static void appendRow(StringBuilder csv, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(cells[i]));
        }
        csv.append("\r\n");
    }

    private static String formatMobile(String mobile) {
        return mobile != null && mobile.length() == 11
                ? mobile.substring(0, 4) + " " + mobile.substring(4, 7) + " " + mobile.substring(7)
                : nullToEmpty(mobile);
    }

    private static String label(RegistrationStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirmed";
            case CHECKED_IN -> "Checked in";
            case WAITLISTED -> "Waitlisted";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String format(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
