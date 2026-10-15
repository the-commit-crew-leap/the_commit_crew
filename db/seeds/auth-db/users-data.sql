INSERT INTO users (username, email, full_name, status, created_at, updated_at) VALUES
    ('alice.johnson', 'alice.johnson@thecommitcrew.com', 'Alice Johnson', 'ACTIVE', NOW(), NOW()),
    ('bob.smith', 'bob.smith@thecommitcrew.com', 'Bob Smith', 'ACTIVE', NOW(), NOW()),
    ('carol.davis', 'carol.davis@thecommitcrew.com', 'Carol Davis', 'ACTIVE', NOW(), NOW()),
    ('david.lee', 'david.lee@thecommitcrew.com', 'David Lee', 'ACTIVE', NOW(), NOW()),
    ('testuser', 'testuser@thecommitcrew.com', 'Test Test', 'ACTIVE', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;