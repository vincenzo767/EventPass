package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One attendee's registration for one event. Status transitions are only possible through the
 * methods below, which guard the allowed state changes:
 *
 * <pre>
 *  WAITLISTED --promote--> CONFIRMED --checkIn--> CHECKED_IN
 *       |                     |  ^                    |
 *       +------cancel---------+  +-----undoCheckIn----+
 *                    v
 *                CANCELLED   (checked-in tickets cannot be cancelled)
 * </pre>
 */
@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 11)
    private String mobile;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 24)
    private String ticketCode;

    @Column(name = "access_token", nullable = false, unique = true, length = 64)
    private String accessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by")
    private Long checkedInBy;

    @Column(name = "check_in_undone_at")
    private Instant checkInUndoneAt;

    @Column(name = "check_in_undone_by")
    private Long checkInUndoneBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Registration() {
    }

    public Registration(Event event, String fullName, String email, String mobile, String ticketCode,
                        String accessToken, RegistrationStatus initialStatus, Instant now) {
        if (initialStatus != RegistrationStatus.CONFIRMED && initialStatus != RegistrationStatus.WAITLISTED) {
            throw new IllegalArgumentException("A new registration must be confirmed or waitlisted");
        }
        this.event = event;
        this.fullName = fullName;
        this.email = email;
        this.mobile = mobile;
        this.ticketCode = ticketCode;
        this.accessToken = accessToken;
        this.status = initialStatus;
        this.registeredAt = now;
        this.confirmedAt = initialStatus == RegistrationStatus.CONFIRMED ? now : null;
        this.updatedAt = now;
    }

    public void checkIn(Long staffUserId, Instant now) {
        requireStatus(RegistrationStatus.CONFIRMED, "Only confirmed tickets can be checked in");
        this.status = RegistrationStatus.CHECKED_IN;
        this.checkedInAt = now;
        this.checkedInBy = staffUserId;
        this.updatedAt = now;
    }

    public void undoCheckIn(Long staffUserId, Instant now) {
        requireStatus(RegistrationStatus.CHECKED_IN, "Only checked-in tickets can have their check-in undone");
        this.status = RegistrationStatus.CONFIRMED;
        this.checkedInAt = null;
        this.checkedInBy = null;
        this.checkInUndoneAt = now;
        this.checkInUndoneBy = staffUserId;
        this.updatedAt = now;
    }

    public void promote(Instant now) {
        requireStatus(RegistrationStatus.WAITLISTED, "Only waitlisted registrations can be promoted");
        this.status = RegistrationStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    /** @param staffUserId the staff member cancelling, or {@code null} when the attendee cancels */
    public void cancel(Long staffUserId, Instant now) {
        if (status != RegistrationStatus.CONFIRMED && status != RegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("Registration in status " + status + " cannot be cancelled");
        }
        this.status = RegistrationStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = staffUserId;
        this.updatedAt = now;
    }

    private void requireStatus(RegistrationStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public Long getCheckedInBy() {
        return checkedInBy;
    }

    public Instant getCheckInUndoneAt() {
        return checkInUndoneAt;
    }

    public Long getCheckInUndoneBy() {
        return checkInUndoneBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }
}
