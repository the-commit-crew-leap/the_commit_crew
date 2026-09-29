INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
    (1, 'AAPL', 50, 150.25),
    (2, 'MSFT', 20, 310.75),
    (3, 'GOOGL', 15, 2750.00)
ON CONFLICT (account_id, symbol) DO NOTHING;