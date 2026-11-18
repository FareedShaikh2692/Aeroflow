CREATE TABLE rpt_flights (
    flight_id       UUID PRIMARY KEY,
    flight_number   VARCHAR(8)  NOT NULL,
    origin          VARCHAR(3)  NOT NULL,
    destination     VARCHAR(3)  NOT NULL,
    departure_time  TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL,
    delay_minutes   INT         NOT NULL DEFAULT 0,
    capacity        INT         NOT NULL,
    booked_seats    INT         NOT NULL DEFAULT 0,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_rpt_flights_departure ON rpt_flights (departure_time);

CREATE TABLE rpt_bookings (
    booking_id      UUID PRIMARY KEY,
    pnr             VARCHAR(6)    NOT NULL,
    flight_id       UUID,
    flight_number   VARCHAR(8)    NOT NULL,
    origin          VARCHAR(3)    NOT NULL,
    destination     VARCHAR(3)    NOT NULL,
    departure_time  TIMESTAMPTZ   NOT NULL,
    contact_email   VARCHAR(254)  NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    passengers      INT           NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    refund_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3)    NOT NULL,
    channel         VARCHAR(20)   NOT NULL,
    synthetic       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL,
    cancelled_at    TIMESTAMPTZ
);
CREATE INDEX ix_rpt_bookings_created ON rpt_bookings (created_at);
CREATE INDEX ix_rpt_bookings_email ON rpt_bookings (lower(contact_email));

CREATE TABLE rpt_checkins (
    event_id        UUID PRIMARY KEY,
    booking_id      UUID        NOT NULL,
    flight_id       UUID        NOT NULL,
    passengers      INT         NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    checked_in_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE rpt_users (
    user_id         UUID PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    roles           VARCHAR(200) NOT NULL,
    registered_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE rpt_activity (
    id          UUID PRIMARY KEY,
    kind        VARCHAR(40)  NOT NULL,
    message     VARCHAR(300) NOT NULL,
    ref         VARCHAR(60),
    occurred_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_rpt_activity_at ON rpt_activity (occurred_at DESC);

CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    actor_id      VARCHAR(40),
    actor_email   VARCHAR(254) NOT NULL,
    actor_roles   VARCHAR(200),
    action        VARCHAR(60)  NOT NULL,
    entity_type   VARCHAR(40)  NOT NULL,
    entity_id     VARCHAR(80),
    details       VARCHAR(2000),
    ip_address    VARCHAR(64),
    source        VARCHAR(40)  NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_occurred ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_action ON audit_log (action);

-- transactional outbox + idempotent inbox (owned by this service)
CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    topic         VARCHAR(120) NOT NULL,
    event_type    VARCHAR(80)  NOT NULL,
    aggregate_id  VARCHAR(120) NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000)
);
CREATE INDEX ix_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL
);
