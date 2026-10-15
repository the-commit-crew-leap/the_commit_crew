FROM postgres:15

ENV POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

# Copy entire db directory with schema, seeds, and indexes
COPY . /docker-entrypoint-initdb.d/

# Make all shell scripts executable
RUN chmod +x /docker-entrypoint-initdb.d/*.sh

VOLUME /var/lib/postgresql/data

EXPOSE 5432