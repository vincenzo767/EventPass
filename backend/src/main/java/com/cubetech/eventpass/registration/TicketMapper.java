package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketEvent;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketResponse;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    private final RegistrationRepository registrations;

    public TicketMapper(RegistrationRepository registrations) {
        this.registrations = registrations;
    }

    public TicketResponse toTicket(Registration registration, Boolean emailQueued) {
        Event event = registration.getEvent();
        return new TicketResponse(
                registration.getTicketCode(),
                registration.getAccessToken(),
                registration.getStatus(),
                registration.getFullName(),
                maskEmail(registration.getEmail()),
                registration.getRegisteredAt(),
                registration.getCheckedInAt(),
                registration.getCancelledAt(),
                waitlistPosition(registration),
                registration.getStatus() == RegistrationStatus.CONFIRMED
                        || registration.getStatus() == RegistrationStatus.WAITLISTED,
                new TicketEvent(event.getId(), event.getTitle(), event.getVenue(), event.getStartsAt(),
                        event.getEndsAt()),
                emailQueued);
    }

    /** 1-based FIFO position, or null when the registration is not waitlisted. */
    public Integer waitlistPosition(Registration registration) {
        if (registration.getStatus() != RegistrationStatus.WAITLISTED) {
            return null;
        }
        return Math.toIntExact(registrations.countWaitlistedAhead(registration.getEvent().getId(),
                registration.getRegisteredAt(), registration.getId()) + 1);
    }

    /** "juan.delacruz@example.com" becomes "j***@example.com". */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
