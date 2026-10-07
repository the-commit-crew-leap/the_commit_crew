DROP TABLE IF EXISTS user_trading_accounts CASCADE;

CREATE TABLE IF NOT EXISTS user_trading_accounts (
    user_id         BIGINT UNIQUE NOT NULL,
    account_id      VARCHAR(32) UNIQUE NOT NULL,
    role            VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);