#!/bin/bash
set -e

# Initialize trading database
psql -v ON_ERROR_STOP=1 -U postgres -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/init-db.sql

# Initialize auth database
psql -v ON_ERROR_STOP=1 -U postgres -d "$AUTH_DB" -f /docker-entrypoint-initdb.d/init-auth-db.sql