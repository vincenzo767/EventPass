-- EventPass initial schema.
-- All timestamps are stored as timestamptz (an absolute instant). The application
-- converts them to Asia/Manila for display and interprets staff input in Asia/Manila.

CREATE TABLE staff_users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT staff_users_username_uq UNIQUE (username),
    CONSTRAINT staff_users_username_lower_ck CHECK (username = lower(username)),
    CONSTRAINT staff_users_role_ck CHECK (role IN ('ORGANIZER', 'DOOR_STAFF'))
);

CREATE TABLE events (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(150) NOT NULL,
    description   TEXT         NOT NULL,
    venue         VARCHAR(200) NOT NULL,
    starts_at     TIMESTAMPTZ  NOT NULL,
    ends_at       TIMESTAMPTZ  NOT NULL,
    capacity      INTEGER      NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    ticket_prefix VARCHAR(5)   NOT NULL,
    -- Stable key for seeded demonstration events (NULL for events created by staff).
    demo_key      VARCHAR(40),
    created_by    BIGINT REFERENCES staff_users (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT events_capacity_ck CHECK (capacity >= 1),
    CONSTRAINT events_time_order_ck CHECK (ends_at > starts_at),
    CONSTRAINT events_status_ck CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT events_ticket_prefix_ck CHECK (ticket_prefix ~ '^[A-Z]{2,5}$'),
    CONSTRAINT events_demo_key_uq UNIQUE (demo_key)
);

CREATE INDEX events_status_starts_at_idx ON events (status, starts_at);

CREATE TABLE registrations (
    id                 BIGSERIAL PRIMARY KEY,
    event_id           BIGINT       NOT NULL REFERENCES events (id),
    full_name          VARCHAR(120) NOT NULL,
    email              VARCHAR(254) NOT NULL,
    mobile             VARCHAR(11)  NOT NULL,
    ticket_code        VARCHAR(24)  NOT NULL,
    -- Unguessable bearer token used in ticket-page links (never encoded in the QR).
    access_token       VARCHAR(64)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    registered_at      TIMESTAMPTZ  NOT NULL,
    confirmed_at       TIMESTAMPTZ,
    checked_in_at      TIMESTAMPTZ,
    checked_in_by      BIGINT REFERENCES staff_users (id),
    check_in_undone_at TIMESTAMPTZ,
    check_in_undone_by BIGINT REFERENCES staff_users (id),
    cancelled_at       TIMESTAMPTZ,
    -- NULL with a cancelled_at value means the attendee cancelled it themselves.
    cancelled_by       BIGINT REFERENCES staff_users (id),
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT registrations_ticket_code_uq UNIQUE (ticket_code),
    CONSTRAINT registrations_access_token_uq UNIQUE (access_token),
    CONSTRAINT registrations_status_ck CHECK (status IN ('CONFIRMED', 'CHECKED_IN', 'WAITLISTED', 'CANCELLED')),
    CONSTRAINT registrations_email_lower_ck CHECK (email = lower(email)),
    CONSTRAINT registrations_mobile_ck CHECK (mobile ~ '^09[0-9]{9}$'),
    CONSTRAINT registrations_checked_in_ck CHECK (
        (status = 'CHECKED_IN' AND checked_in_at IS NOT NULL) OR (status <> 'CHECKED_IN')
    )
);

-- One active (non-cancelled) registration per email per event. Cancelled rows are
-- excluded, so cancelling frees the email for re-registration. This also makes
-- concurrent duplicate submissions fail atomically at the database level.
CREATE UNIQUE INDEX registrations_active_email_uq
    ON registrations (event_id, email)
    WHERE status <> 'CANCELLED';

-- Supports capacity counts and FIFO waitlist ordering.
CREATE INDEX registrations_event_status_idx ON registrations (event_id, status, registered_at, id);

CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    staff_user_id BIGINT       NOT NULL REFERENCES staff_users (id),
    action        VARCHAR(40)  NOT NULL,
    target_type   VARCHAR(30)  NOT NULL,
    target_id     BIGINT       NOT NULL,
    details       VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX audit_logs_created_at_idx ON audit_logs (created_at DESC, id DESC);
