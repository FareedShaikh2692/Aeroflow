CREATE TABLE passengers (
    id               UUID PRIMARY KEY,
    user_id          UUID UNIQUE,
    first_name       VARCHAR(80)  NOT NULL,
    last_name        VARCHAR(80)  NOT NULL,
    email            VARCHAR(254) NOT NULL,
    phone            VARCHAR(30),
    date_of_birth    DATE,
    nationality      VARCHAR(2),
    passport_number  VARCHAR(20),
    passport_expiry  DATE,
    address_line     VARCHAR(200),
    city             VARCHAR(80),
    country          VARCHAR(80),
    loyalty_tier     VARCHAR(20)  NOT NULL DEFAULT 'BLUE',
    loyalty_points   INT          NOT NULL DEFAULT 0,
    notes            VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_passengers_email ON passengers (lower(email));
CREATE INDEX ix_passengers_name ON passengers (lower(last_name), lower(first_name));

-- Read model built from booking/check-in events (no cross-service table access)
CREATE TABLE travel_history (
    id               UUID PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,
    booking_id       UUID         NOT NULL,
    pnr              VARCHAR(6)   NOT NULL,
    flight_number    VARCHAR(8)   NOT NULL,
    origin           VARCHAR(3)   NOT NULL,
    destination      VARCHAR(3)   NOT NULL,
    departure_time   TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    amount           NUMERIC(12,2),
    currency         VARCHAR(3),
    checked_in       BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_history_booking_email UNIQUE (booking_id, email)
);
CREATE INDEX ix_history_email ON travel_history (lower(email));

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
