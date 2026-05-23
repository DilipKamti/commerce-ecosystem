CREATE TABLE IF NOT EXISTS categories (
    id                 BINARY(16) PRIMARY KEY,
    name               VARCHAR(100) NOT NULL UNIQUE,
    parent_category_id BINARY(16),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         DATETIME,
    updated_at         DATETIME
);

CREATE TABLE IF NOT EXISTS products (
    id          BINARY(16) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       DECIMAL(10, 2) NOT NULL,
    sku         VARCHAR(100) NOT NULL UNIQUE,
    category_id BINARY(16) REFERENCES categories(id),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME,
    updated_at  DATETIME
);

-- Seed some categories
INSERT IGNORE INTO categories (id, name) VALUES (UUID(), 'Electronics');
INSERT IGNORE INTO categories (id, name) VALUES (UUID(), 'Clothing');
INSERT IGNORE INTO categories (id, name) VALUES (UUID(), 'Books');