package com.cubetech.eventpass.registration;

import com.cubetech.eventpass.common.RateLimiter;
import com.cubetech.eventpass.config.AppProperties;
import com.cubetech.eventpass.registration.RegistrationDtos.RegistrationRequest;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketLookupRequest;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicRegistrationController {

    private final RegistrationService registrationService;
    private final QrCodeGenerator qrCodeGenerator;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;

    public PublicRegistrationController(RegistrationService registrationService, QrCodeGenerator qrCodeGenerator,
                                        RateLimiter rateLimiter, AppProperties properties) {
        this.registrationService = registrationService;
        this.qrCodeGenerator = qrCodeGenerator;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/events/{eventId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse register(@PathVariable Long eventId, @Valid @RequestBody RegistrationRequest request,
                                   HttpServletRequest http) {
        rateLimiter.check("registration", http.getRemoteAddr(), properties.rateLimit().registration());
        return registrationService.register(eventId, request);
    }

    @PostMapping("/tickets/lookup")
    public TicketResponse lookup(@Valid @RequestBody TicketLookupRequest request, HttpServletRequest http) {
        rateLimiter.check("ticket-lookup", http.getRemoteAddr(), properties.rateLimit().ticketLookup());
        return registrationService.lookup(request);
    }

    @GetMapping("/tickets/{token}")
    public ResponseEntity<TicketResponse> ticket(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(registrationService.getByAccessToken(token));
    }

    @GetMapping(value = "/tickets/{token}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable String token) {
        byte[] png = qrCodeGenerator.png(registrationService.qrPayloadForToken(token));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(png);
    }

    @PostMapping("/tickets/{token}/cancel")
    public TicketResponse cancel(@PathVariable String token, HttpServletRequest http) {
        rateLimiter.check("ticket-lookup", http.getRemoteAddr(), properties.rateLimit().ticketLookup());
        return registrationService.cancelByAttendee(token);
    }
}
