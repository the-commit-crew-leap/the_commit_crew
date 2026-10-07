-- The Commit Crew Trading Database Initialization

-- This script creates the schema only. Seed data is populated separately.

-- Step 1: Create tables with constraints
\i /docker-entrypoint-initdb.d/schema/trading-db/instruments.sql
\i /docker-entrypoint-initdb.d/schema/trading-db/price_history.sql
\i /docker-entrypoint-initdb.d/schema/trading-db/accounts.sql
\i /docker-entrypoint-initdb.d/schema/trading-db/positions.sql
\i /docker-entrypoint-initdb.d/schema/trading-db/orders.sql

-- Step 2: Create indexes for performance
\i /docker-entrypoint-initdb.d/indexes/indexes.sql