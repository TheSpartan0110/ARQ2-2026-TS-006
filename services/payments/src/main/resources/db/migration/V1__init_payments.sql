-- Payments service schema (Spec 2.2, 4.3 item 5).
-- The outbox and processed_messages tables come from messaging V0.1 (classpath:db/messaging).

CREATE TABLE IF NOT EXISTS payments (
    id                       UUID PRIMARY KEY,
    order_id                 UUID        NOT NULL UNIQUE, -- one logical charge per order (Spec 2.2)
    total_amount_minor       BIGINT      NOT NULL CHECK (total_amount_minor >= 0),
    currency                 VARCHAR(3)  NOT NULL,
    status                   VARCHAR(30) NOT NULL,        -- PENDING | UNKNOWN | AUTHORIZED | FAILED | ...
    provider_idempotency_key VARCHAR(100) NOT NULL,       -- derived from orderId, reused on every retry
    provider_reference       VARCHAR(100),
    failure_code             VARCHAR(50),
    encrypted_payment_token  TEXT,                        -- removed once the outcome is definitive
    correlation_id           UUID        NOT NULL,
    command_message_id       UUID        NOT NULL,        -- causationId of the events emitted for this payment
    traceparent              VARCHAR(100),
    version                  INTEGER     NOT NULL DEFAULT 1,
    pending_since            TIMESTAMP WITH TIME ZONE,    -- set when the outcome becomes UNKNOWN
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_payments_open ON payments (created_at) WHERE status IN ('PENDING', 'UNKNOWN');
