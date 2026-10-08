DROP TABLE IF EXISTS refresh_tokens CASCADE;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    user_id         BIGINT NOT NULL,
    token_hash      VARCHAR(64) UNIQUE NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP NOT NULL,
    revoked_at      TIMESTAMP,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    PRIMARY KEY (user_id, created_at),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);