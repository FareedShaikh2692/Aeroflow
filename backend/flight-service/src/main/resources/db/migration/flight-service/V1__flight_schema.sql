CREATE TABLE airports (
    code        VARCHAR(3) PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    city        VARCHAR(80)  NOT NULL,
    country     VARCHAR(80)  NOT NULL,
    timezone    VARCHAR(40)  NOT NULL
);

CREATE TABLE aircraft (
    id               UUID PRIMARY KEY,
    registration     VARCHAR(10)  NOT NULL UNIQUE,
    model            VARCHAR(60)  NOT NULL,
    manufacturer     VARCHAR(60)  NOT NULL,
    business_rows    INT          NOT NULL CHECK (business_rows >= 0),
    business_layout  VARCHAR(20)  NOT NULL,
    economy_rows     INT          NOT NULL CHECK (economy_rows > 0),
    economy_layout   VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE TABLE flights (
    id                   UUID PRIMARY KEY,
    flight_number        VARCHAR(8)    NOT NULL,
    origin_code          VARCHAR(3)    NOT NULL REFERENCES airports (code),
    destination_code     VARCHAR(3)    NOT NULL REFERENCES airports (code),
    aircraft_id          UUID          NOT NULL REFERENCES aircraft (id),
    departure_time       TIMESTAMPTZ   NOT NULL,
    arrival_time         TIMESTAMPTZ   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    delay_minutes        INT           NOT NULL DEFAULT 0,
    status_reason        VARCHAR(300),
    gate                 VARCHAR(6),
    terminal             VARCHAR(4),
    economy_fare         NUMERIC(10,2) NOT NULL,
    business_fare        NUMERIC(10,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_route CHECK (origin_code <> destination_code),
    CONSTRAINT ck_times CHECK (arrival_time > departure_time)
);
CREATE UNIQUE INDEX ux_flight_number_departure ON flights (flight_number, departure_time);
CREATE INDEX ix_flights_route_departure ON flights (origin_code, destination_code, departure_time);
CREATE INDEX ix_flights_departure ON flights (departure_time);
CREATE INDEX ix_flights_status ON flights (status);

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
