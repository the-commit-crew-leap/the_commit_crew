CREATE INDEX IF NOT EXISTS idx_users_username 
    ON users(username);

CREATE INDEX IF NOT EXISTS idx_users_email 
    ON users(email);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash 
    ON refresh_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id_status 
    ON refresh_tokens(user_id, status);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at 
    ON refresh_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_user_trading_accounts_account_id 
    ON user_trading_accounts(account_id);