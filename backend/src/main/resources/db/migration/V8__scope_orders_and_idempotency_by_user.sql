ALTER TABLE orders ADD COLUMN user_id UUID;
UPDATE orders SET user_id = '00000000-0000-4000-8000-000000000099' WHERE user_id IS NULL;
ALTER TABLE orders ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT fk_orders_user_id FOREIGN KEY (user_id) REFERENCES app_users(id);
CREATE INDEX idx_orders_user_id_created_at ON orders(user_id, created_at DESC);

ALTER TABLE checkout_idempotency ADD COLUMN user_id UUID;
UPDATE checkout_idempotency SET user_id = '00000000-0000-4000-8000-000000000099' WHERE user_id IS NULL;
ALTER TABLE checkout_idempotency ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE checkout_idempotency ADD CONSTRAINT fk_checkout_idempotency_user_id FOREIGN KEY (user_id) REFERENCES app_users(id);
ALTER TABLE checkout_idempotency DROP CONSTRAINT checkout_idempotency_pkey;
ALTER TABLE checkout_idempotency ADD CONSTRAINT checkout_idempotency_pkey PRIMARY KEY (user_id, idempotency_key);
