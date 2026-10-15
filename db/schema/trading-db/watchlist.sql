DROP TABLE IF EXISTS watchlist CASCADE;

CREATE TABLE IF NOT EXISTS watchlist (
    account_id  VARCHAR(32) NOT NULL,
    symbol      VARCHAR(20) NOT NULL,
    added_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, symbol),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE,
    FOREIGN KEY (symbol) REFERENCES instruments(symbol) ON DELETE RESTRICT ON UPDATE CASCADE
);