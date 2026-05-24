CREATE TABLE IF NOT EXISTS orders (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   DATETIME,
    updated_at   DATETIME
);

CREATE TABLE IF NOT EXISTS order_items (
    id           VARCHAR(36) PRIMARY KEY,
    order_id     VARCHAR(36) NOT NULL REFERENCES orders(id),
    product_id   VARCHAR(36) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price   DECIMAL(10, 2) NOT NULL,
    quantity     INT NOT NULL
);