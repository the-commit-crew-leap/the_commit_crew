FROM postgres:15

ENV POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

# Install Python and pip for fetching market data
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Copy Python script and requirements
COPY python/requirements.txt /tmp/
COPY python/src/fetch_market_data.py /tmp/

# Install Python dependencies and generate market data
RUN pip install --no-cache-dir --break-system-packages -r /tmp/requirements.txt && \
    cd /tmp && python3 fetch_market_data.py

# Copy entire db directory with schema, seeds, and indexes
COPY db/ /docker-entrypoint-initdb.d/

# Copy generated market data CSV files
RUN mkdir -p /docker-entrypoint-initdb.d/data && \
    cp /market_data/price_history.csv /docker-entrypoint-initdb.d/data/ && \
    cp /market_data/ticker_metadata.csv /docker-entrypoint-initdb.d/data/

# Make all shell scripts executable
RUN chmod +x /docker-entrypoint-initdb.d/*.sh

VOLUME /var/lib/postgresql/data

EXPOSE 5432
