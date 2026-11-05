-- Exact transport-ID search without a source queue cannot use the existing
-- (source_queue, jms_message_id) unique index efficiently.
CREATE INDEX received_message_jms_message_id_idx ON messaging.received_message (jms_message_id);
