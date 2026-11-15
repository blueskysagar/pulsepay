CREATE TABLE transfers(
    id UUID PRIMARY KEY,
    source_wallet_id UUID NOT NULL,
    destination_wallet_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- WHY:
    -- Optimistic locking allows Hibernate to detect concurrent
    -- updates to the same transfer record.
    version BIGINT NOT NULL DEFAULT 0,
    -- WHY:
    -- A transfer must reference real wallets inside the current
    -- Wallet Service database.
    CONSTRAINT fk_transfer_source_wallet
                      FOREIGN KEY (source_wallet_id)
                      REFERENCES wallet(id),
    CONSTRAINT fk_transfer_destination_wallet
                      FOREIGN KEY (destination_wallet_id)
                      REFERENCES wallet(id),
    -- WHY:
    -- A transfer of zero or negative money is not meaningful.
    -- We protect this invariant at the database layer as well.
    CONSTRAINT chk_transfer_amount_positive
                      CHECK (amount>0),
    -- WHY:
    -- A wallet cannot transfer money to itself.
    CONSTRAINT chk_transfer_different_wallets
                      CHECK ( source_wallet_id <> destination_wallet_id )

);
-- WHY:
-- We expect queries that retrieve transfers sent by a wallet.
CREATE INDEX idx_transfer_source_wallet
ON transfers(source_wallet_id);
-- WHY:
-- We expect queries that retrieve transfers received by a wallet.
CREATE INDEX idx_transfer_destination_wallet
ON transfers(destination_wallet_id);
-- WHY:
-- Supports queries for recent transfers and transfer history.
CREATE INDEX idx_transfer_created_at
ON transfers(created_at);
