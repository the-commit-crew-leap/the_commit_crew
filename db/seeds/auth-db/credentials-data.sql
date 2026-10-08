INSERT INTO credentials (user_id, password_hash, algorithm, last_login, failed_login_attempts, locked_until, created_at, updated_at) VALUES
    (1, '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5YmMxSUlburn66', 'BCRYPT', NULL, 0, NULL, NOW(), NOW()),
    (2, '$2b$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'BCRYPT', NULL, 0, NULL, NOW(), NOW()),
    (3, '$2b$12$qZfwXQsHGYjcVahzLDwI.eKV71s8qKlNQQqKBhjn8BhVjWMsOKlFy', 'BCRYPT', NULL, 0, NULL, NOW(), NOW()),
    (4, '$2b$12$8XCFvBBMjJFhSLd8q3VSwu8RFy8pTGMqzgf1R4T3vShPZj8sJN9T6', 'BCRYPT', NULL, 0, NULL, NOW(), NOW()),
    (5, '$2b$12$KIX/KmqvF5NYFjSZM6hWkOqXbmvNxaL4sUu0F9t1A.d6QKgMjkGMi', 'BCRYPT', NULL, 0, NULL, NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;