-- Clears existing data and reinserts fresh data
TRUNCATE TABLE orders RESTART IDENTITY CASCADE;
TRUNCATE TABLE positions RESTART IDENTITY CASCADE;
TRUNCATE TABLE price_history RESTART IDENTITY CASCADE;
TRUNCATE TABLE accounts RESTART IDENTITY CASCADE;
TRUNCATE TABLE instruments RESTART IDENTITY CASCADE;

\i /docker-entrypoint-initdb.d/seeds/instruments-data.sql
\i /docker-entrypoint-initdb.d/seeds/price_history-data.sql
\i /docker-entrypoint-initdb.d/seeds/accounts-data.sql
\i /docker-entrypoint-initdb.d/seeds/positions-data.sql
\i /docker-entrypoint-initdb.d/seeds/orders-data.sql