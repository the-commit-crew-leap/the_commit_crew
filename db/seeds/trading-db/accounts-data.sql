INSERT INTO accounts (account_id, holder_name, cash_balance, status, version) VALUES
    ('ACC-1001', 'Alice Johnson', 25000.00, 'ACTIVE', 0),
    ('ACC-1002', 'Bob Smith', 5400.50, 'ACTIVE', 0),
    ('ACC-1003', 'Carol Davis', 120000.00, 'ACTIVE', 0),
    ('ACC-1004', 'David Lee', 0.00, 'CLOSED', 0),
    ('ACC-1005', 'Test Test', 100000.00, 'ACTIVE', 0)
ON CONFLICT (account_id) DO NOTHING;