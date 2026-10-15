DROP TABLE IF EXISTS positions CASCADE;

CREATE TABLE IF NOT EXISTS positions (
    account_id      BIGINT NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    quantity        NUMERIC(14,4) NOT NULL CHECK (quantity > 0),
    average_cost    NUMERIC(14,4) NOT NULL CHECK (average_cost > 0),
    PRIMARY KEY (account_id, symbol),
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    FOREIGN KEY (symbol) REFERENCES instruments(symbol) ON DELETE RESTRICT ON UPDATE CASCADE
);