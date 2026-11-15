ALTER TABLE idempotency_record
ADD COLUMN resource_id UUID;
ALTER TABLE idempotency_record
ALTER COLUMN idempotency_key TYPE VARCHAR(100);