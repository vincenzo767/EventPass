package com.cubetech.eventpass.demo;

import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the seeded demo events demonstrable on any day. When a rolling demo event has ended, it is
 * moved forward to its original offset from today (same start and end times). Cebu Dev Meetup 2026 is
 * therefore always "today" so the event-day check-in rule can be shown, and the full event stays in
 * the future. Events created by staff are never touched. Disable with {@code DEMO_ROLLING_DATES=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo", name = "rolling-dates", havingValue = "true")
public class DemoDateRoller {

    private static final Logger log = LoggerFactory.getLogger(DemoDateRoller.class);

    private final EventRepository events;
    private final Clock clock;

    public DemoDateRoller(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        rollForward();
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Manila")
    @Transactional
    public void rollForward() {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, TimeConfig.EVENT_ZONE);
        for (Event event : events.findByDemoKeyIsNotNull()) {
            DemoEvents.byKey(event.getDemoKey())
                    .filter(DemoEvents.Definition::rolling)
                    .filter(definition -> event.hasEnded(now))
                    .ifPresent(definition -> {
                        ZonedDateTime start = event.getStartsAt().atZone(TimeConfig.EVENT_ZONE);
                        ZonedDateTime end = event.getEndsAt().atZone(TimeConfig.EVENT_ZONE);
                        LocalDate target = today.plusDays(definition.dayOffset());
                        if (!target.atTime(end.toLocalTime()).atZone(TimeConfig.EVENT_ZONE).toInstant().isAfter(now)) {
                            target = target.plusDays(1);
                        }
                        Instant newStart = target.atTime(start.toLocalTime()).atZone(TimeConfig.EVENT_ZONE).toInstant();
                        Instant newEnd = target.atTime(end.toLocalTime()).atZone(TimeConfig.EVENT_ZONE).toInstant();
                        event.reschedule(newStart, newEnd, now);
                        events.save(event);
                        log.info("Rolled demo event '{}' forward to {}", event.getTitle(), target);
                    });
        }
    }
}
