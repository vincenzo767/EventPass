package com.cubetech.eventpass.event;

import com.cubetech.eventpass.event.EventDtos.PublicEventResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {

    private final EventService eventService;

    public PublicEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** Published events that have not yet ended, soonest first. */
    @GetMapping
    public List<PublicEventResponse> list() {
        return eventService.listPublic();
    }

    @GetMapping("/{id}")
    public PublicEventResponse get(@PathVariable Long id) {
        return eventService.getPublic(id);
    }
}
