INSERT INTO orders (account_id, symbol, side, quantity, price, status, idempotencyKey) VALUES
    ('ACC-1001', 'AAPL', 'BUY', 50, 150.25, 'FILLED', '0000000001'),
    ('ACC-1002', 'MSFT', 'BUY', 20, 310.75, 'FILLED', '0000000002'),
    ('ACC-1003', 'GOOGL', 'BUY', 15, 2750.00, 'FILLED', '0000000003'),
    ('ACC-1001', 'AAPL', 'SELL', 10, 155.00, 'NEW', '0000000004'),
    ('ACC-1002', 'MSFT', 'BUY', 5, 305.00, 'CANCELLED', '0000000005')
ON CONFLICT (idempotencyKey) DO UPDATE SET
    account_id = EXCLUDED.account_id,
    symbol = EXCLUDED.symbol,
    side = EXCLUDED.side,
    quantity = EXCLUDED.quantity,
    price = EXCLUDED.price,
    status = EXCLUDED.status;