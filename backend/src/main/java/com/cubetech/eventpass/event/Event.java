package com.cubetech.eventpass.event;

import com.cubetech.eventpass.config.TimeConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "ticket_prefix", nullable = false, length = 5)
    private String ticketPrefix;

    @Column(name = "demo_key", length = 40)
    private String demoKey;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
    }

    public Event(String title, String description, String venue, Instant startsAt, Instant endsAt, int capacity,
                 EventStatus status, String ticketPrefix, Long createdBy, Instant now) {
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.status = status;
        this.ticketPrefix = ticketPrefix;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String description, String venue, Instant startsAt, Instant endsAt,
                       int capacity, EventStatus status, String ticketPrefix, Instant now) {
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.status = status;
        this.ticketPrefix = ticketPrefix;
        this.updatedAt = now;
    }

    public void reschedule(Instant startsAt, Instant endsAt, Instant now) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = now;
    }

    public void changeStatus(EventStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public boolean hasEnded(Instant now) {
        return !now.isBefore(endsAt);
    }

    public boolean hasStarted(Instant now) {
        return !now.isBefore(startsAt);
    }

    /** True when {@code date} (Cebu time) falls on the event's calendar day(s). */
    public boolean isEventDay(LocalDate date) {
        LocalDate first = startsAt.atZone(TimeConfig.EVENT_ZONE).toLocalDate();
        LocalDate last = endsAt.atZone(TimeConfig.EVENT_ZONE).toLocalDate();
        return !date.isBefore(first) && !date.isAfter(last);
    }

    public void markAsDemo(String demoKey) {
        this.demoKey = demoKey;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getTicketPrefix() {
        return ticketPrefix;
    }

    public String getDemoKey() {
        return demoKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
