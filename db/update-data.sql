-- Clears existing data and reinserts fresh data
TRUNCATE TABLE orders RESTART IDENTITY CASCADE;
TRUNCATE TABLE positions RESTART IDENTITY CASCADE;
TRUNCATE TABLE price_history RESTART IDENTITY CASCADE;
TRUNCATE TABLE accounts RESTART IDENTITY CASCADE;
TRUNCATE TABLE instruments RESTART IDENTITY CASCADE;

\i /docker-entrypoint-initdb.d/seeds/trading-db/instruments-data.sql
\i /docker-entrypoint-initdb.d/seeds/trading-db/price_history-data.sql
\i /docker-entrypoint-initdb.d/seeds/trading-db/accounts-data.sql
\i /docker-entrypoint-initdb.d/seeds/trading-db/positions-data.sql
\i /docker-entrypoint-initdb.d/seeds/trading-db/orders-data.sql