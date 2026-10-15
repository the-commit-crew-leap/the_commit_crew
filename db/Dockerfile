FROM postgres:15

ENV POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

# Copy entire db directory with schema, seeds, and indexes
COPY db/ /docker-entrypoint-initdb.d/

# Copy generated market data CSV files
RUN mkdir -p /docker-entrypoint-initdb.d/data

COPY analytics/outputs/csv/price_history.csv /docker-entrypoint-initdb.d/data/
COPY analytics/outputs/csv/symbol_metadata.csv /docker-entrypoint-initdb.d/data/

# Make all shell scripts executable
RUN chmod +x /docker-entrypoint-initdb.d/*.sh

VOLUME /var/lib/postgresql/data

EXPOSE 5432