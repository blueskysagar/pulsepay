CREATE TABLE refresh_sessions(
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_refresh_sessions PRIMARY KEY (id),
    CONSTRAINT fk_refresh_session_account FOREIGN KEY (account_id) REFERENCES identity_account (id),
    CONSTRAINT uk_refresh_session_token_hash UNIQUE (token_hash)


);
CREATE INDEX idx_refresh_session_account_id
ON refresh_sessions (account_id);
