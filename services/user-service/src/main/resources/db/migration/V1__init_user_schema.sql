CREATE TABLE IF NOT EXISTS roles (
    id          BINARY(16) PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id            BINARY(16) PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    DATETIME,
    updated_at    DATETIME
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BINARY(16) NOT NULL REFERENCES users(id),
    role_id BINARY(16) NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

INSERT IGNORE INTO roles (id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_CUSTOMER');
INSERT IGNORE INTO roles (id, name) VALUES (UUID_TO_BIN(UUID()), 'ROLE_ADMIN');