CREATE TABLE transfer_completion_records(
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_transfer_completion_records_transfer_id
ON transfer_completion_records (transfer_id);