package com.cubetech.eventpass.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** A clock tests can move, so time-based rules (event day, no-shows, ended events) are deterministic. */
public final class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClockView(this, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    private static final class MutableClockView extends Clock {
        private final MutableClock source;
        private final ZoneId zone;

        private MutableClockView(MutableClock source, ZoneId zone) {
            this.source = source;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClockView(source, zone);
        }

        @Override
        public Instant instant() {
            return source.instant();
        }
    }
}
