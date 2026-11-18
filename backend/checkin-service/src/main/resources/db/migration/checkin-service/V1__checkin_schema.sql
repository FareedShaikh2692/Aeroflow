CREATE TABLE checkins (
    id                     UUID PRIMARY KEY,
    booking_id             UUID         NOT NULL,
    pnr                    VARCHAR(6)   NOT NULL,
    passenger_id           UUID         NOT NULL UNIQUE,
    first_name             VARCHAR(80)  NOT NULL,
    last_name              VARCHAR(80)  NOT NULL,
    flight_id              UUID         NOT NULL,
    flight_number          VARCHAR(8)   NOT NULL,
    origin                 VARCHAR(3)   NOT NULL,
    destination            VARCHAR(3)   NOT NULL,
    departure_time         TIMESTAMPTZ  NOT NULL,
    seat_number            VARCHAR(4)   NOT NULL,
    cabin                  VARCHAR(10)  NOT NULL,
    boarding_pass_number   VARCHAR(20)  NOT NULL UNIQUE,
    sequence_number        INT          NOT NULL,
    boarding_group         VARCHAR(2)   NOT NULL,
    gate                   VARCHAR(6),
    boarding_time          TIMESTAMPTZ  NOT NULL,
    channel                VARCHAR(20)  NOT NULL,
    checked_in_by          VARCHAR(254),
    checked_in_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_flight_sequence UNIQUE (flight_id, sequence_number)
);
CREATE INDEX ix_checkins_pnr ON checkins (pnr);
CREATE INDEX ix_checkins_flight ON checkins (flight_id);

CREATE TABLE flight_sequences (
    flight_id  UUID PRIMARY KEY,
    last_seq   INT NOT NULL
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
