-- References learned from consumed events (Spec 2.4) and the durable store of premature events (Spec 4.3 item 3).

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_id  UUID;  -- from PaymentAuthorized, used by RefundPayment
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_id UUID;  -- from DeliveryAssigned
ALTER TABLE orders ADD COLUMN IF NOT EXISTS driver_id   UUID;  -- from DeliveryAssigned

CREATE TABLE IF NOT EXISTS pending_events (
    id         BIGSERIAL PRIMARY KEY,
    message_id UUID        NOT NULL UNIQUE,
    order_id   UUID        NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload    TEXT        NOT NULL,                  -- the OrderEvent record as JSON
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | QUARANTINED
    stored_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pending_events_order ON pending_events (order_id) WHERE status = 'PENDING';
