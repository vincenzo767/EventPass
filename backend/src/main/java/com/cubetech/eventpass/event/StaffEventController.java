package com.cubetech.eventpass.event;

import com.cubetech.eventpass.event.EventDtos.EventRequest;
import com.cubetech.eventpass.event.EventDtos.StaffEventResponse;
import com.cubetech.eventpass.event.EventDtos.StatusChangeRequest;
import com.cubetech.eventpass.staff.StaffPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/events")
public class StaffEventController {

    private final EventService eventService;

    public StaffEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<StaffEventResponse> list(@AuthenticationPrincipal StaffPrincipal staff) {
        return eventService.listForStaff(staff);
    }

    @GetMapping("/{id}")
    public StaffEventResponse get(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal staff) {
        return eventService.getForStaff(id, staff);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public StaffEventResponse create(@Valid @RequestBody EventRequest request,
                                     @AuthenticationPrincipal StaffPrincipal staff) {
        return eventService.create(request, staff);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public StaffEventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request,
                                     @AuthenticationPrincipal StaffPrincipal staff) {
        return eventService.update(id, request, staff);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public StaffEventResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                           @AuthenticationPrincipal StaffPrincipal staff) {
        return eventService.changeStatus(id, request.status(), staff);
    }
}
