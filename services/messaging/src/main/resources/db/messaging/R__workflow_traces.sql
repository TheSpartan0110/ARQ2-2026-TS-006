-- Stored workflow trace context (Spec 6.3): the traceparent of the first message this service received for an order.
-- Later restaurant and driver HTTP actions start their own traces and add a span link to it.
-- Repeatable migration, so it also applies to schemas whose versioned migrations already ran past V0.1.
CREATE TABLE IF NOT EXISTS workflow_traces (
    order_id    UUID PRIMARY KEY,
    traceparent TEXT        NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
