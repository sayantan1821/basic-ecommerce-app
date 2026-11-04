CREATE TABLE webhook_events (
    provider_event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    result VARCHAR(24) NOT NULL CHECK (result IN ('PROCESSED', 'IGNORED', 'REJECTED'))
);
