-- Flyway migration for Identity & Access Service (Schema: identity)

CREATE TABLE IF NOT EXISTS identity.profiles (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    roles VARCHAR(100) NOT NULL,
    restaurant_id UUID,
    driver_id UUID,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_email ON identity.profiles(email);
CREATE INDEX IF NOT EXISTS idx_profiles_roles ON identity.profiles(roles);

CREATE TABLE IF NOT EXISTS identity.idempotency_records (
    scope_key VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Initial seed data for system profiles (Spec 3.2)
-- Default password: password123
INSERT INTO identity.profiles (id, display_name, email, roles, restaurant_id, driver_id, password_hash, active, created_at, updated_at)
VALUES 
    ('00000000-0000-4000-8000-000000000001', 'Alex', 'alex@example.test', 'USER', NULL, NULL, '$2a$10$g9VW6O/YPxuta1zPJYa2YuRkzLQnknLX9y2rk5tc0p/HJB8tA1As.', TRUE, NOW(), NOW()),
    ('00000000-0000-4000-8000-000000000099', 'Admin Operator', 'admin@eatsnow.test', 'ADMIN', NULL, NULL, '$2a$10$g9VW6O/YPxuta1zPJYa2YuRkzLQnknLX9y2rk5tc0p/HJB8tA1As.', TRUE, NOW(), NOW()),
    ('00000000-0000-4000-8000-000000000021', 'Sam', 'sam@example.test', 'DRIVER', NULL, '00000000-0000-4000-8000-000000000020', '$2a$10$g9VW6O/YPxuta1zPJYa2YuRkzLQnknLX9y2rk5tc0p/HJB8tA1As.', TRUE, NOW(), NOW()),
    ('00000000-0000-4000-8000-000000000010', 'Elena (Bistro)', 'elena@example.test', 'RESTAURANT', '00000000-0000-4000-8000-000000000050', NULL, '$2a$10$g9VW6O/YPxuta1zPJYa2YuRkzLQnknLX9y2rk5tc0p/HJB8tA1As.', TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
