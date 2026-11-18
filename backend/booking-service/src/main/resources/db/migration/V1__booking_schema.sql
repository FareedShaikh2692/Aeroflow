-- Local projection + seat inventory counter per flight. Row is locked (SELECT ... FOR UPDATE)
-- for the duration of any booking / seat-change transaction on that flight.
CREATE TABLE flight_inventory (
    flight_id         UUID PRIMARY KEY,
    flight_number     VARCHAR(8)  NOT NULL,
    capacity          INT         NOT NULL,
    booked_seats      INT         NOT NULL DEFAULT 0 CHECK (booked_seats >= 0),
    status            VARCHAR(20) NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_capacity CHECK (booked_seats <= capacity)
);

CREATE TABLE bookings (
    id                    UUID PRIMARY KEY,
    pnr                   VARCHAR(6)    NOT NULL UNIQUE,
    flight_id             UUID          NOT NULL,
    flight_number         VARCHAR(8)    NOT NULL,
    origin                VARCHAR(3)    NOT NULL,
    destination           VARCHAR(3)    NOT NULL,
    departure_time        TIMESTAMPTZ   NOT NULL,
    arrival_time          TIMESTAMPTZ   NOT NULL,
    user_id               UUID,
    contact_email         VARCHAR(254)  NOT NULL,
    contact_phone         VARCHAR(30),
    status                VARCHAR(20)   NOT NULL,
    total_amount          NUMERIC(12,2) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    payment_reference     VARCHAR(40),
    card_last4            VARCHAR(4),
    card_brand            VARCHAR(20),
    channel               VARCHAR(20)   NOT NULL,
    created_by            VARCHAR(254),
    cancellation_reason   VARCHAR(300),
    cancelled_at          TIMESTAMPTZ,
    reminder_sent         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_bookings_flight ON bookings (flight_id);
CREATE INDEX ix_bookings_user ON bookings (user_id);
CREATE INDEX ix_bookings_email ON bookings (lower(contact_email));
CREATE INDEX ix_bookings_created ON bookings (created_at);
CREATE INDEX ix_bookings_reminder ON bookings (departure_time) WHERE status = 'CONFIRMED' AND reminder_sent = FALSE;

CREATE TABLE booking_passengers (
    id               UUID PRIMARY KEY,
    booking_id       UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    first_name       VARCHAR(80)   NOT NULL,
    last_name        VARCHAR(80)   NOT NULL,
    date_of_birth    DATE,
    nationality      VARCHAR(2),
    passport_number  VARCHAR(20),
    email            VARCHAR(254),
    cabin            VARCHAR(10)   NOT NULL,
    seat_number      VARCHAR(4)    NOT NULL,
    fare             NUMERIC(10,2) NOT NULL,
    checked_in       BOOLEAN       NOT NULL DEFAULT FALSE,
    checked_in_at    TIMESTAMPTZ
);
CREATE INDEX ix_booking_passengers_booking ON booking_passengers (booking_id);

-- Seat allocation. The partial unique index is the last line of defence against double booking.
CREATE TABLE seat_assignments (
    id             UUID PRIMARY KEY,
    flight_id      UUID        NOT NULL,
    seat_number    VARCHAR(4)  NOT NULL,
    passenger_id   UUID        NOT NULL REFERENCES booking_passengers (id) ON DELETE CASCADE,
    released       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL,
    released_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_seat_active ON seat_assignments (flight_id, seat_number) WHERE released = FALSE;

CREATE TABLE payments (
    id               UUID PRIMARY KEY,
    booking_id       UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    type             VARCHAR(10)   NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    provider_ref     VARCHAR(40)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE TABLE idempotency_keys (
    idem_key       VARCHAR(80) PRIMARY KEY,
    request_hash   VARCHAR(64) NOT NULL,
    booking_id     UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

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
