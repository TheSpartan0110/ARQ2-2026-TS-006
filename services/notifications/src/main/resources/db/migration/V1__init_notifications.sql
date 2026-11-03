-- Notifications service schema (Spec 2.2 Notification, 4.2.4, ADR 006).
-- The outbox and processed_messages tables come from messaging V0.1 (classpath:db/messaging).

-- One email per (orderId, trigger): the unique constraint makes redelivered messages harmless.
CREATE TABLE IF NOT EXISTS notifications (
    id              UUID PRIMARY KEY,
    order_id        UUID        NOT NULL,
    customer_id     UUID        NOT NULL,
    trigger_name    VARCHAR(20) NOT NULL,            -- ACCEPTED | ON_THE_WAY | DELIVERED | REJECTED | PAYMENT_FAILED | REFUNDED
    channel         VARCHAR(10) NOT NULL DEFAULT 'EMAIL',
    status          VARCHAR(10) NOT NULL,            -- PENDING | SENT | FAILED
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id  UUID        NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notifications_order_trigger UNIQUE (order_id, trigger_name)
);
CREATE INDEX IF NOT EXISTS idx_notifications_due ON notifications (next_attempt_at) WHERE status = 'PENDING';

-- Highest OrderStatusChanged version processed per order (Spec 4.2.2): older versions are acknowledged without sending.
CREATE TABLE IF NOT EXISTS order_versions (
    order_id        UUID PRIMARY KEY,
    highest_version INTEGER NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
