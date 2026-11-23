package com.cubetech.eventpass.registration;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registration totals for one event, computed from database rows. */
public record EventCounts(long confirmed, long checkedIn, long waitlisted, long cancelled) {

    public static final EventCounts EMPTY = new EventCounts(0, 0, 0, 0);

    /** Seats taken: confirmed plus checked-in registrations. */
    public long registered() {
        return confirmed + checkedIn;
    }

    public long spotsLeft(int capacity) {
        return Math.max(0, capacity - registered());
    }

    public static Map<Long, EventCounts> from(List<RegistrationRepository.StatusCount> rows) {
        Map<Long, long[]> raw = new HashMap<>();
        for (RegistrationRepository.StatusCount row : rows) {
            long[] totals = raw.computeIfAbsent(row.getEventId(), id -> new long[4]);
            totals[row.getStatus().ordinal()] += row.getTotal();
        }
        Map<Long, EventCounts> result = new HashMap<>();
        raw.forEach((eventId, t) -> result.put(eventId, new EventCounts(
                t[RegistrationStatus.CONFIRMED.ordinal()],
                t[RegistrationStatus.CHECKED_IN.ordinal()],
                t[RegistrationStatus.WAITLISTED.ordinal()],
                t[RegistrationStatus.CANCELLED.ordinal()])));
        return result;
    }

    public static Map<Long, EventCounts> load(RegistrationRepository repository, Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return from(repository.countByStatusForEvents(eventIds));
    }
}
