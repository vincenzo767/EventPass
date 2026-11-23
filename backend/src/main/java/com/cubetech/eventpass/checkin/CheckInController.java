package com.cubetech.eventpass.checkin;

import com.cubetech.eventpass.common.RateLimiter;
import com.cubetech.eventpass.config.AppProperties;
import com.cubetech.eventpass.staff.StaffPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class CheckInController {

    private final CheckInService checkInService;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;

    public CheckInController(CheckInService checkInService, RateLimiter rateLimiter, AppProperties properties) {
        this.checkInService = checkInService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/events/{eventId}/check-ins")
    public CheckInResponse checkIn(@PathVariable Long eventId, @Valid @RequestBody CheckInRequest request,
                                   @AuthenticationPrincipal StaffPrincipal staff) {
        rateLimiter.check("check-in", "staff-" + staff.id(), properties.rateLimit().checkIn());
        return checkInService.checkIn(eventId, request.ticketCode(), staff);
    }

    @PostMapping("/registrations/{registrationId}/undo-check-in")
    public CheckInResponse undo(@PathVariable Long registrationId, @AuthenticationPrincipal StaffPrincipal staff) {
        return checkInService.undo(registrationId, staff);
    }

    public record CheckInRequest(
            @NotBlank(message = "Enter a ticket code.")
            @Size(max = 64, message = "Invalid ticket")
            String ticketCode) {
    }
}
