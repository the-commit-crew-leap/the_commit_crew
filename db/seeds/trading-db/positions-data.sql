INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    ('ACC-1001', 'AAPL', 50, 150.25),
    ('ACC-1002', 'MSFT', 20, 310.75),
    ('ACC-1003', 'GOOGL', 15, 2750.00)
ON CONFLICT (account_id, symbol) DO NOTHING;