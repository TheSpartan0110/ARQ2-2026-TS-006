-- Demo data for the restaurant user seeded by Identity ("Elena (Bistro)", restaurantId ...0050), so the full
-- order flow can be exercised with a RESTAURANT token (Spec 3.2: restaurantId must be an existing Catalog restaurant).
INSERT INTO restaurants (id, name, available) VALUES
    ('00000000-0000-4000-8000-000000000050', 'Elena Bistro', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_items (id, restaurant_id, name, amount_minor, currency, available, version) VALUES
    ('00000000-0000-4000-8000-000000000051', '00000000-0000-4000-8000-000000000050', 'Pasta Carbonara', 3200000, 'COP', true, 1),
    ('00000000-0000-4000-8000-000000000052', '00000000-0000-4000-8000-000000000050', 'Tiramisu', 1500000, 'COP', true, 1)
ON CONFLICT (id) DO NOTHING;
