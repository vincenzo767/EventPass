package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.registration.RegistrationDtos.AttendeeListResponse;
import com.cubetech.eventpass.registration.RegistrationDtos.AttendeeResponse;
import com.cubetech.eventpass.registration.RegistrationDtos.PromotionResponse;
import com.cubetech.eventpass.registration.StaffRegistrationService.ExportData;
import com.cubetech.eventpass.staff.StaffPrincipal;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffRegistrationController {

    private final StaffRegistrationService service;

    public StaffRegistrationController(StaffRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/events/{eventId}/registrations")
    public AttendeeListResponse list(@PathVariable Long eventId, @RequestParam(required = false) String q,
                                     @AuthenticationPrincipal StaffPrincipal staff) {
        return service.listAttendees(eventId, q, staff);
    }

    @GetMapping("/events/{eventId}/registrations/export")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<byte[]> export(@PathVariable Long eventId, @AuthenticationPrincipal StaffPrincipal staff) {
        ExportData data = service.exportData(eventId, staff);
        String slug = data.event().getTitle().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String filename = slug + "-attendees-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(AttendeeCsvWriter.write(data.attendees()));
    }

    @PostMapping("/registrations/{registrationId}/cancel")
    @PreAuthorize("hasRole('ORGANIZER')")
    public AttendeeResponse cancel(@PathVariable Long registrationId, @AuthenticationPrincipal StaffPrincipal staff) {
        return service.cancel(registrationId, staff);
    }

    @PostMapping("/events/{eventId}/waitlist/promote")
    @PreAuthorize("hasRole('ORGANIZER')")
    public PromotionResponse promote(@PathVariable Long eventId, @AuthenticationPrincipal StaffPrincipal staff) {
        return service.promoteNext(eventId, staff);
    }
}
