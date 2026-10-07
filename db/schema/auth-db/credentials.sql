DROP TABLE IF EXISTS credentials CASCADE;

CREATE TABLE IF NOT EXISTS credentials (
    user_id                 BIGINT PRIMARY KEY NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    algorithm               VARCHAR(64) NOT NULL,
    last_login              TIMESTAMP,
    failed_login_attempts   INT DEFAULT 0,
    locked_until            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE OR REPLACE FUNCTION update_credentials_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_credentials_updated_at
BEFORE UPDATE ON credentials
FOR EACH ROW
EXECUTE FUNCTION update_credentials_updated_at();