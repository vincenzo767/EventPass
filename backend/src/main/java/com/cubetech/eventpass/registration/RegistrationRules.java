package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.common.ApiException;

/** Business-rule checks that produce user-facing errors before an entity state change is attempted. */
final class RegistrationRules {

    private RegistrationRules() {
    }

    static void requireCancellable(Registration registration) {
        switch (registration.getStatus()) {
            case CHECKED_IN -> throw ApiException.conflict("ALREADY_CHECKED_IN",
                    "This attendee has already checked in, so the registration cannot be cancelled.");
            case CANCELLED -> throw ApiException.conflict("ALREADY_CANCELLED",
                    "This registration has already been cancelled.");
            default -> {
                // CONFIRMED and WAITLISTED registrations can be cancelled.
            }
        }
    }
}
