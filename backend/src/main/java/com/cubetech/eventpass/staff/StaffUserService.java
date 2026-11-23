package com.cubetech.eventpass.staff;

import com.cubetech.eventpass.audit.AuditAction;
import com.cubetech.eventpass.audit.AuditLog.TargetType;
import com.cubetech.eventpass.audit.AuditService;
import com.cubetech.eventpass.common.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organizer-only management of staff accounts. */
@Service
public class StaffUserService {

    private final StaffUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public StaffUserService(StaffUserRepository repository, PasswordEncoder passwordEncoder, AuditService audit,
                            Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffUserResponse> list() {
        return repository.findAllByOrderByRoleAscNameAsc().stream().map(StaffUserResponse::of).toList();
    }

    @Transactional
    public StaffUserResponse create(CreateStaffRequest request, StaffPrincipal actor) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByUsername(username)) {
            throw ApiException.fieldError("username", "This username is already taken.");
        }
        StaffUser user = repository.save(new StaffUser(request.name().trim(), username,
                passwordEncoder.encode(request.password()), request.role(), Instant.now(clock)));
        audit.record(actor, AuditAction.STAFF_CREATED, TargetType.STAFF_USER, user.getId(),
                "Created " + user.getRole() + " account \"" + user.getUsername() + "\"");
        return StaffUserResponse.of(user);
    }

    @Transactional
    public StaffUserResponse setActive(Long id, boolean active, StaffPrincipal actor) {
        if (id.equals(actor.id()) && !active) {
            throw ApiException.conflict("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account.");
        }
        StaffUser user = repository.findById(id).orElseThrow(() -> ApiException.notFound("Staff member not found."));
        user.setActive(active);
        audit.record(actor, AuditAction.STAFF_UPDATED, TargetType.STAFF_USER, user.getId(),
                (active ? "Activated" : "Deactivated") + " account \"" + user.getUsername() + "\"");
        return StaffUserResponse.of(user);
    }

    public record CreateStaffRequest(
            @NotBlank(message = "Name is required.") @Size(max = 120, message = "Name is too long.") String name,
            @NotBlank(message = "Username is required.")
            @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                    message = "Use 3–50 letters, numbers, dots, dashes or underscores.") String username,
            @NotBlank(message = "Password is required.")
            @Size(min = 10, max = 128, message = "Password must be at least 10 characters.") String password,
            @NotNull(message = "Role is required.") StaffRole role) {
    }

    public record StaffUserResponse(Long id, String name, String username, StaffRole role, boolean active,
                                    Instant createdAt) {

        static StaffUserResponse of(StaffUser user) {
            return new StaffUserResponse(user.getId(), user.getName(), user.getUsername(), user.getRole(),
                    user.isActive(), user.getCreatedAt());
        }
    }
}
