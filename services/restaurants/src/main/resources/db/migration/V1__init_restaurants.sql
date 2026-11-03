-- Restaurants service schema (Spec 2.2 RestaurantOrder, 3.5).
-- The outbox and processed_messages tables come from messaging V0.1 (classpath:db/messaging).

CREATE TABLE IF NOT EXISTS restaurant_orders (
    order_id       UUID PRIMARY KEY,                 -- the global orderId is the identifier (Spec 3.5)
    restaurant_id  UUID         NOT NULL,
    items          TEXT         NOT NULL,            -- JSON snapshot received with RequestRestaurantAcceptance
    status         VARCHAR(20)  NOT NULL,            -- PENDING | ACCEPTED | REJECTED | PREPARING
    reason         VARCHAR(300),
    correlation_id UUID         NOT NULL,            -- original workflow correlation id (Spec 4.4)
    version        INTEGER      NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_inbox ON restaurant_orders (restaurant_id, created_at, order_id);

CREATE TABLE IF NOT EXISTS idempotency_records (
    scope_key     VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    status_code   INT,
    response_body TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_records (created_at);
