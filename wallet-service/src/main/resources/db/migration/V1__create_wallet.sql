CREATE TABLE wallet(
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_wallet_account_currency
                   UNIQUE (account_id, currency)

);
