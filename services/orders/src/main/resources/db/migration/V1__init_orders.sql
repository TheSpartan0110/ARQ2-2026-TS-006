-- Orders service schema (Spec 2.2, 2.4, 3.1).
-- The outbox and processed_messages tables come from messaging V0.1 (classpath:db/messaging).

CREATE TABLE IF NOT EXISTS orders (
    id                 UUID PRIMARY KEY,
    customer_id        UUID        NOT NULL,
    restaurant_id      UUID        NOT NULL,
    quote_id           UUID        NOT NULL UNIQUE, -- one order per quote (Spec 2.3)
    items              TEXT        NOT NULL,        -- immutable JSON snapshot of the quote items
    total_amount_minor BIGINT      NOT NULL CHECK (total_amount_minor >= 0),
    currency           VARCHAR(3)  NOT NULL,
    delivery_address   TEXT        NOT NULL,        -- JSON snapshot
    status             VARCHAR(30) NOT NULL,
    payment_status     VARCHAR(30) NOT NULL,
    correlation_id     UUID        NOT NULL,        -- original workflow correlation id (Spec 4.4)
    version            INTEGER     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders (customer_id);

-- Order state history: every change of status or paymentStatus.
CREATE TABLE IF NOT EXISTS order_status_history (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            UUID        NOT NULL REFERENCES orders (id),
    from_status         VARCHAR(30),
    to_status           VARCHAR(30) NOT NULL,
    from_payment_status VARCHAR(30),
    to_payment_status   VARCHAR(30) NOT NULL,
    order_version       INTEGER     NOT NULL,
    caused_by           UUID,                       -- messageId that triggered the change
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Encrypted payment method token: kept only until Payments reports an outcome (Spec 3.4).
CREATE TABLE IF NOT EXISTS order_secrets (
    order_id                UUID PRIMARY KEY REFERENCES orders (id) ON DELETE CASCADE,
    encrypted_payment_token TEXT NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- "Send each command once per order" (Spec 2.4).
CREATE TABLE IF NOT EXISTS sent_commands (
    order_id     UUID        NOT NULL,
    command_type VARCHAR(60) NOT NULL,
    sent_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (order_id, command_type)
);

-- Idempotency-Key records (Spec 3.1), same layout as the other services.
CREATE TABLE IF NOT EXISTS idempotency_records (
    scope_key     VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    status_code   INT,
    response_body TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_records (created_at);
