CREATE TABLE orders (
    id UUID PRIMARY KEY,
    reference_id VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_FAILED', 'EXPIRED', 'CANCELLED')),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal_paise BIGINT NOT NULL CHECK (subtotal_paise >= 0),
    total_paise BIGINT NOT NULL CHECK (total_paise >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    sku_snapshot VARCHAR(80) NOT NULL,
    product_name_snapshot VARCHAR(180) NOT NULL,
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise >= 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    line_total_paise BIGINT NOT NULL CHECK (line_total_paise >= 0),
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
    failure_code VARCHAR(80),
    mock_client_secret VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE checkout_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    created_at TIMESTAMPTZ NOT NULL
);
