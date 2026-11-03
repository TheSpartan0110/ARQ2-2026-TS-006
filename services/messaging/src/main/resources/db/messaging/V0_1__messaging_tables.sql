-- Tables every messaging service needs (ADR 001, Spec 4.3). Created in the service's own schema.
-- Version 0.1 runs before the service's own V1; add classpath:db/messaging to spring.flyway.locations.

-- Transactional outbox: written in the same transaction as the business change.
CREATE TABLE IF NOT EXISTS outbox (
    id              BIGSERIAL PRIMARY KEY,
    message_id      UUID        NOT NULL UNIQUE,
    exchange        TEXT        NOT NULL,
    routing_key     TEXT        NOT NULL,
    body            TEXT        NOT NULL,
    order_id        UUID,
    correlation_id  UUID,
    traceparent     TEXT,
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;

-- Idempotent consumer: one row per message already processed by a consumer.
CREATE TABLE IF NOT EXISTS processed_messages (
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer, message_id)
);
