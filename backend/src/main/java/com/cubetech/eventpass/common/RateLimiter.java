package com.cubetech.eventpass.common;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Small in-memory fixed-window rate limiter keyed by (bucket, client).
 *
 * <p>Adequate for a single application instance. A multi-instance deployment would keep
 * these counters in a shared store such as Redis.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records one attempt and throws {@link RateLimitExceededException} once the per-minute limit is exceeded. */
    public void check(String bucket, String clientKey, int limitPerMinute) {
        long now = clock.millis();
        long windowStart = now - (now % WINDOW_MILLIS);
        Window window = windows.compute(bucket + "|" + clientKey, (key, existing) ->
                existing == null || existing.start != windowStart ? new Window(windowStart) : existing);
        if (window.count.incrementAndGet() > limitPerMinute) {
            long retryAfter = Math.max(1, (windowStart + WINDOW_MILLIS - now) / 1000);
            throw new RateLimitExceededException(retryAfter);
        }
    }

    /** Clears all counters (used by tests). */
    public void reset() {
        windows.clear();
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpired() {
        long cutoff = clock.millis() - WINDOW_MILLIS;
        windows.values().removeIf(window -> window.start < cutoff);
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }
    }
}
