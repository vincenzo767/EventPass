package com.cubetech.eventpass.event;

import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.registration.EventCounts;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public final class EventDtos {

    private EventDtos() {
    }

    /** Event as seen by attendees. Contains no registration details beyond seat availability. */
    public record PublicEventResponse(
            Long id,
            String title,
            String description,
            String venue,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            long spotsLeft,
            Availability availability,
            boolean registrationOpen,
            boolean waitlistOpen,
            boolean happeningNow) {

        public static PublicEventResponse of(Event event, EventCounts counts, Instant now) {
            Availability availability = Availability.of(event, counts, now);
            return new PublicEventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getVenue(),
                    event.getStartsAt(), event.getEndsAt(), event.getCapacity(),
                    counts.spotsLeft(event.getCapacity()), availability,
                    availability == Availability.OPEN, availability == Availability.FULL,
                    event.hasStarted(now) && !event.hasEnded(now));
        }
    }

    /** Registration totals shown on staff screens. {@code noShows} is null until the event has started. */
    public record EventStats(long capacity, long registered, long confirmed, long checkedIn, long waitlisted,
                             long cancelled, long spotsLeft, Long noShows) {

        public static EventStats of(Event event, EventCounts counts, Instant now) {
            return new EventStats(event.getCapacity(), counts.registered(), counts.confirmed(), counts.checkedIn(),
                    counts.waitlisted(), counts.cancelled(), counts.spotsLeft(event.getCapacity()),
                    event.hasStarted(now) ? counts.confirmed() : null);
        }
    }

    public record StaffEventResponse(
            Long id,
            String title,
            String description,
            String venue,
            Instant startsAt,
            Instant endsAt,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            int capacity,
            EventStatus status,
            String ticketPrefix,
            Availability availability,
            boolean started,
            boolean ended,
            boolean eventDay,
            EventStats stats,
            Instant updatedAt) {

        public static StaffEventResponse of(Event event, EventCounts counts, Instant now) {
            ZonedDateTime start = event.getStartsAt().atZone(TimeConfig.EVENT_ZONE);
            ZonedDateTime end = event.getEndsAt().atZone(TimeConfig.EVENT_ZONE);
            return new StaffEventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getVenue(),
                    event.getStartsAt(), event.getEndsAt(), start.toLocalDate(), start.toLocalTime(),
                    end.toLocalTime(), event.getCapacity(), event.getStatus(), event.getTicketPrefix(),
                    Availability.of(event, counts, now), event.hasStarted(now), event.hasEnded(now),
                    event.isEventDay(now.atZone(TimeConfig.EVENT_ZONE).toLocalDate()),
                    EventStats.of(event, counts, now), event.getUpdatedAt());
        }
    }

    /** Create/update payload. Date and times are Cebu local time (Asia/Manila). */
    public record EventRequest(
            @NotBlank(message = "Title is required.")
            @Size(max = 150, message = "Title must be at most 150 characters.")
            String title,

            @NotBlank(message = "Description is required.")
            @Size(max = 5000, message = "Description must be at most 5000 characters.")
            String description,

            @NotBlank(message = "Venue is required.")
            @Size(max = 200, message = "Venue must be at most 200 characters.")
            String venue,

            @NotNull(message = "Event date is required.")
            LocalDate date,

            @NotNull(message = "Start time is required.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime startTime,

            @NotNull(message = "End time is required.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime endTime,

            @NotNull(message = "Capacity is required.")
            @Min(value = 1, message = "Capacity must be at least 1.")
            @Max(value = 100000, message = "Capacity must be at most 100,000.")
            Integer capacity,

            @NotNull(message = "Status is required.")
            EventStatus status,

            @Pattern(regexp = "^$|^[A-Za-z]{2,5}$", message = "Ticket prefix must be 2 to 5 letters.")
            String ticketPrefix) {
    }

    public record StatusChangeRequest(@NotNull(message = "Status is required.") EventStatus status) {
    }
}
