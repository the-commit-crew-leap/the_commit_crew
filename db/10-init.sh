#!/bin/bash
set -e

# Create databases if they don't exist
echo "Creating databases..."
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = '$POSTGRES_DB'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE \"$POSTGRES_DB\";"
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = '$AUTH_DB'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE \"$AUTH_DB\";"

# Initialize trading database
echo "Initializing trading database ($POSTGRES_DB)..."
psql -v ON_ERROR_STOP=1 -U postgres -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/init-db.sql

# Initialize auth database
echo "Initializing auth database ($AUTH_DB)..."
psql -v ON_ERROR_STOP=1 -U postgres -d "$AUTH_DB" -f /docker-entrypoint-initdb.d/init-auth-db.sql