CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BINARY(16) PRIMARY KEY,
    token       VARCHAR(512) NOT NULL UNIQUE,
    user_id     BINARY(16) NOT NULL,
    created_at  DATETIME,
    expires_at  DATETIME NOT NULL
);

-- Index for fast token lookup
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);