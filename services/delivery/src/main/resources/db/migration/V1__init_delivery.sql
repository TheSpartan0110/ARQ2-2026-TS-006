-- Delivery service schema (Spec 2.2 Driver and Delivery, 3.6).
-- The outbox and processed_messages tables come from messaging V0.1 (classpath:db/messaging).

CREATE TABLE IF NOT EXISTS drivers (
    id                 UUID PRIMARY KEY,                -- the driverId claim of the Identity token
    profile_id         UUID        NOT NULL,
    on_duty            BOOLEAN     NOT NULL DEFAULT FALSE,
    active_delivery_id UUID,                             -- at most one active delivery per driver
    last_assigned_at   TIMESTAMP WITH TIME ZONE,        -- fairness: longest time since the last assignment first
    version            INTEGER     NOT NULL DEFAULT 1,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_drivers_assignable ON drivers (last_assigned_at NULLS FIRST)
    WHERE on_duty = TRUE AND active_delivery_id IS NULL;

CREATE TABLE IF NOT EXISTS deliveries (
    id                 UUID PRIMARY KEY,
    order_id           UUID        NOT NULL UNIQUE,     -- one delivery per order (Spec 2.2)
    restaurant_id      UUID        NOT NULL,
    driver_id          UUID,                             -- required from ASSIGNED onward
    delivery_address   TEXT        NOT NULL,             -- JSON; visible only to the assigned driver
    status             VARCHAR(20) NOT NULL,             -- UNASSIGNED | ASSIGNED | PICKED_UP | DELIVERED
    correlation_id     UUID        NOT NULL,             -- original workflow correlation id (Spec 4.4)
    command_message_id UUID        NOT NULL,             -- causationId of the system-triggered DeliveryAssigned
    version            INTEGER     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_deliveries_unassigned ON deliveries (created_at) WHERE status = 'UNASSIGNED';
CREATE INDEX IF NOT EXISTS idx_deliveries_driver ON deliveries (driver_id, created_at, id);

CREATE TABLE IF NOT EXISTS idempotency_records (
    scope_key     VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    status_code   INT,
    response_body TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_records (created_at);
