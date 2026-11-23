package com.cubetech.eventpass.staff;

import com.cubetech.eventpass.staff.StaffUserService.CreateStaffRequest;
import com.cubetech.eventpass.staff.StaffUserService.StaffUserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/users")
@PreAuthorize("hasRole('ORGANIZER')")
public class StaffUserController {

    private final StaffUserService service;

    public StaffUserController(StaffUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffUserResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffUserResponse create(@Valid @RequestBody CreateStaffRequest request,
                                    @AuthenticationPrincipal StaffPrincipal actor) {
        return service.create(request, actor);
    }

    @PatchMapping("/{id}")
    public StaffUserResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request,
                                       @AuthenticationPrincipal StaffPrincipal actor) {
        return service.setActive(id, request.active(), actor);
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }
}
