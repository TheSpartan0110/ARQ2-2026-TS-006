-- EatsNow Catalog Service Schema Migration (ADR 004, Spec 2.2)

CREATE TABLE IF NOT EXISTS restaurants (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS menu_items (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    available BOOLEAN NOT NULL DEFAULT true,
    version INT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    scope_key VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_code INT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_menu_items_restaurant_id ON menu_items(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_records(created_at);

-- Initial seed data matching docs/spec.md examples
INSERT INTO restaurants (id, name, available) VALUES
    ('00000000-0000-4000-8000-000000000002', 'Burger House', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_items (id, restaurant_id, name, amount_minor, currency, available, version) VALUES
    ('00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000002', 'Classic Burger', 2500000, 'COP', true, 1),
    ('00000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000002', 'Cheese Fries', 1200000, 'COP', true, 1),
    ('00000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000002', 'Craft Soda', 600000, 'COP', true, 1)
ON CONFLICT (id) DO NOTHING;
