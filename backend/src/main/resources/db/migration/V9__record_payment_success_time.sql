ALTER TABLE payments ADD COLUMN paid_at TIMESTAMPTZ;

UPDATE payments
SET paid_at = updated_at
WHERE status = 'SUCCEEDED';
