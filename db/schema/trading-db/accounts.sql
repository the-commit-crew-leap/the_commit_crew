DROP TABLE IF EXISTS accounts CASCADE;

CREATE TABLE IF NOT EXISTS accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(32) UNIQUE NOT NULL,
    holder_name     VARCHAR(255) NOT NULL,
    cash_balance    NUMERIC(18,2) NOT NULL CHECK (cash_balance >= 0),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED', 'SUSPENDED')),
    version         INT DEFAULT 0,
    last_updated    TIMESTAMP DEFAULT NOW()
);