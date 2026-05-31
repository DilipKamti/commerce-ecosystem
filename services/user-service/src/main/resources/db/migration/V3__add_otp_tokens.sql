CREATE TABLE IF NOT EXISTS otp_tokens (
    id           BINARY(36) PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    otp          VARCHAR(6) NOT NULL,
    created_at   DATETIME,
    expires_at   DATETIME NOT NULL,
    used         BOOLEAN NOT NULL DEFAULT FALSE
);

-- Indexes for faster lookup
CREATE INDEX idx_otp_tokens_email ON otp_tokens(email);
CREATE INDEX idx_otp_tokens_otp ON otp_tokens(otp);
CREATE INDEX idx_otp_tokens_expires_at ON otp_tokens(expires_at);