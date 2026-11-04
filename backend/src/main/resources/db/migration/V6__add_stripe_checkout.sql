ALTER TABLE payments DROP COLUMN mock_client_secret;
ALTER TABLE payments ADD COLUMN stripe_checkout_session_id VARCHAR(255) UNIQUE;
ALTER TABLE payments ADD COLUMN stripe_checkout_url TEXT;
ALTER TABLE payments ADD COLUMN stripe_checkout_expires_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN stripe_payment_intent_id VARCHAR(255) UNIQUE;

UPDATE payments SET provider = 'stripe';
