ALTER TABLE transfer_completion_records
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;