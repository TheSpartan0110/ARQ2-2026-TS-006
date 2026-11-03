-- One logical refund per payment (Spec 4.2.2, 4.3 item 6).

CREATE TABLE IF NOT EXISTS refunds (
    id                       UUID PRIMARY KEY,
    payment_id               UUID        NOT NULL UNIQUE REFERENCES payments (id),
    order_id                 UUID        NOT NULL,
    amount_minor             BIGINT      NOT NULL CHECK (amount_minor >= 0),
    currency                 VARCHAR(3)  NOT NULL,
    status                   VARCHAR(20) NOT NULL,        -- PENDING | UNKNOWN | REFUNDED | FAILED
    provider_idempotency_key VARCHAR(100) NOT NULL,       -- derived from paymentId, reused on every retry
    charge_idempotency_key   VARCHAR(100) NOT NULL,       -- identifies the original charge at the provider
    provider_reference       VARCHAR(100),
    failure_code             VARCHAR(50),
    correlation_id           UUID        NOT NULL,
    command_message_id       UUID        NOT NULL,
    traceparent              VARCHAR(100),
    version                  INTEGER     NOT NULL DEFAULT 1,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_refunds_open ON refunds (created_at) WHERE status IN ('PENDING', 'UNKNOWN', 'FAILED');
