INSERT INTO user_trading_accounts (user_id, account_id, role, created_at) VALUES
    (1, 'ACC-1001', 'CLIENT', NOW()),
    (2, 'ACC-1002', 'CLIENT', NOW()),
    (3, 'ACC-1003', 'CLIENT', NOW()),
    (4, 'ACC-1004', 'CLIENT', NOW()),
    (5, 'ACC-1005', 'CLIENT', NOW())
ON CONFLICT (account_id, user_id) DO NOTHING;