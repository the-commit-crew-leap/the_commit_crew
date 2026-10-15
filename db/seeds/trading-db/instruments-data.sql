INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
    ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', TRUE),
    ('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', TRUE),
    ('GOOGL', 'Alphabet Inc.', 'EQUITY', 'USD', TRUE),
    ('US10Y', 'US Treasury 10-Year Note', 'BOND', 'USD', TRUE),
    ('VFIAX', 'Vanguard 500 Index Fund', 'FUND', 'USD', TRUE),
    ('CASH-USD', 'US Dollar Cash', 'CASH', 'USD', FALSE)
ON CONFLICT (symbol) DO NOTHING;