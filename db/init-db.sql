-- The Commit Crew Database Initialization

-- This script creates the schema only. Seed data is populated separately.

-- Step 1: Create tables with constraints
\i /docker-entrypoint-initdb.d/schema/instruments.sql
\i /docker-entrypoint-initdb.d/schema/price_history.sql
\i /docker-entrypoint-initdb.d/schema/accounts.sql
\i /docker-entrypoint-initdb.d/schema/positions.sql
\i /docker-entrypoint-initdb.d/schema/orders.sql

-- Step 2: Create indexes for performance
\i /docker-entrypoint-initdb.d/indexes/indexes.sql