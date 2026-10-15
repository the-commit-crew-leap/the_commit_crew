-- The Commit Crew Auth Database Initialization
\c auth_db-dev

-- This script creates the auth schema and seed data.

-- Step 1: Create tables with constraints
\i /docker-entrypoint-initdb.d/schema/auth-db/users.sql
\i /docker-entrypoint-initdb.d/schema/auth-db/credentials.sql
\i /docker-entrypoint-initdb.d/schema/auth-db/refresh_tokens.sql
\i /docker-entrypoint-initdb.d/schema/auth-db/user_trading_accounts.sql

-- Step 2: Create indexes for performance
\i /docker-entrypoint-initdb.d/indexes/auth-indexes.sql