package com.cubetech.eventpass.staff;

import com.cubetech.eventpass.common.ApiException;
import com.cubetech.eventpass.common.RateLimiter;
import com.cubetech.eventpass.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository, RateLimiter rateLimiter, AppProperties properties) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfAuthenticationStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    public StaffMeResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
                                 HttpServletResponse response) {
        rateLimiter.check("login", request.getRemoteAddr(), properties.rateLimit().login());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        } catch (AuthenticationException ex) {
            // Same message for unknown user, wrong password and disabled account (no user enumeration).
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect username or password.");
        }

        // Session fixation protection: always start a fresh session on sign-in.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Rotate the CSRF token after authentication.
        csrfAuthenticationStrategy.onAuthentication(authentication, request, response);

        return StaffMeResponse.of((StaffPrincipal) authentication.getPrincipal());
    }

    /**
     * The signed-in staff member, or 204 No Content for anonymous visitors. Public pages check the
     * session on load, so an anonymous visit is a normal outcome rather than an error.
     */
    @GetMapping("/me")
    public ResponseEntity<StaffMeResponse> me(@AuthenticationPrincipal StaffPrincipal staff) {
        return staff == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(StaffMeResponse.of(staff));
    }

    /** Any request issues the XSRF-TOKEN cookie; this endpoint lets the SPA obtain it explicitly. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required.") @Size(max = 50) String username,
            @NotBlank(message = "Password is required.") @Size(max = 128) String password) {
    }

    public record StaffMeResponse(Long id, String name, String username, StaffRole role) {

        static StaffMeResponse of(StaffPrincipal staff) {
            return new StaffMeResponse(staff.id(), staff.name(), staff.getUsername(), staff.role());
        }
    }
}
