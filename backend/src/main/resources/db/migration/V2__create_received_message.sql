CREATE TABLE messaging.received_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_queue TEXT NOT NULL,
    jms_message_id TEXT NOT NULL,
    correlation_id TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_type TEXT NOT NULL,
    payload_size INTEGER NOT NULL CHECK (payload_size >= 0),
    payload TEXT NOT NULL,
    CONSTRAINT received_message_transport_identity UNIQUE (source_queue, jms_message_id)
);
CREATE INDEX received_message_received_at_idx ON messaging.received_message (received_at DESC, id DESC);
