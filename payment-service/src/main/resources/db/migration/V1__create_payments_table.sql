CREATE TABLE payments(
    id UUID PRIMARY KEY,
    source_wallet_id UUID NOT NULL,
    destination_wallet_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    status VARCHAR(20) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    version BIGINT NOT NULL

);
CREATE index idx_payments_source_wallet_id
ON payments(source_wallet_id);
CREATE index idx_payments_destination_wallet_id
ON payments(destination_wallet_id);
CREATE index idx_payments_status
ON payments(status);