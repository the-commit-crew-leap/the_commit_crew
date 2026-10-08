-- Clears existing auth data and reinserts fresh data
\c auth_db-dev

TRUNCATE TABLE refresh_tokens RESTART IDENTITY CASCADE;
TRUNCATE TABLE user_trading_accounts RESTART IDENTITY CASCADE;
TRUNCATE TABLE credentials RESTART IDENTITY CASCADE;
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

\i /docker-entrypoint-initdb.d/seeds/auth-db/users-data.sql
\i /docker-entrypoint-initdb.d/seeds/auth-db/credentials-data.sql
\i /docker-entrypoint-initdb.d/seeds/auth-db/user_trading-data.sql