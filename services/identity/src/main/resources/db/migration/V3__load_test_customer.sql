-- Synthetic customer of the load scenarios (load/run-load.ps1, Spec 6.7), so their orders never mix with the demo
-- customer's. Password: password123
INSERT INTO identity.profiles (id, display_name, email, roles, restaurant_id, driver_id, password_hash, active, created_at, updated_at)
VALUES
    ('00000000-0000-4000-8000-000000000088', 'Load Test', 'loadtest@example.test', 'USER', NULL, NULL, '$2a$10$g9VW6O/YPxuta1zPJYa2YuRkzLQnknLX9y2rk5tc0p/HJB8tA1As.', TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
