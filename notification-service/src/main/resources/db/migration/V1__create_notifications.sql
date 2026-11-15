CREATE TABLE notifications(
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    transfer_id UUID NOT NULL,
    recipient_account_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notifications_event_recipient
                          UNIQUE (event_id,recipient_account_id)
);
CREATE INDEX idx_notifications_recipient_created
ON notifications(recipient_account_id, created_at DESC );
