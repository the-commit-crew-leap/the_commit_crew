DROP TABLE IF EXISTS orders CASCADE;

CREATE TABLE IF NOT EXISTS orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       VARCHAR(32) NOT NULL,
    symbol           VARCHAR(20) NOT NULL,
    side             VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity         INT NOT NULL CHECK (quantity > 0),
    price            NUMERIC(18,2) NOT NULL CHECK (price > 0),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    idempotencyKey   VARCHAR(32) NOT NULL UNIQUE,
    created_on       TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE RESTRICT,
    FOREIGN KEY (symbol) REFERENCES instruments(symbol) ON DELETE RESTRICT ON UPDATE CASCADE
);