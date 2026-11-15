ALTER TABLE payments
ADD COLUMN idempotency_key VARCHAR(100) NOT NULL;
CREATE UNIQUE INDEX uk_payments_idempotency_key
ON payments(idempotency_key);