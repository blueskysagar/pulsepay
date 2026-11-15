CREATE TABLE outbox_events(
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100)NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);
CREATE INDEX idx_outbox_event_status_created_at
ON outbox_events(status, created_at);