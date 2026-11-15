CREATE TABLE idempotency_record(
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    idempotency_key VARCHAR(30) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_idempotency_account_key_operation
                               UNIQUE (account_id, idempotency_key, operation)


);