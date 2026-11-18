CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    event_id      UUID         NOT NULL,
    event_type    VARCHAR(80)  NOT NULL,
    channel       VARCHAR(10)  NOT NULL,
    recipient     VARCHAR(254) NOT NULL,
    subject       VARCHAR(200),
    body          TEXT         NOT NULL,
    pnr           VARCHAR(6),
    status        VARCHAR(10)  NOT NULL,
    provider_ref  VARCHAR(60),
    error         VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_notification_event_channel_recipient UNIQUE (event_id, channel, recipient)
);
CREATE INDEX ix_notifications_recipient ON notifications (lower(recipient));
CREATE INDEX ix_notifications_created ON notifications (created_at DESC);

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
