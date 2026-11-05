package com.bank.messaging.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Read model of messaging.received_message; rows are inserted idempotently by {@link ReceivedMessageJpaRepository#insertIfAbsent}. */
@Entity
@Immutable
@Table(schema = "messaging", name = "received_message")
public class ReceivedMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_queue", nullable = false)
    private String sourceQueue;

    @Column(name = "jms_message_id", nullable = false)
    private String jmsMessageId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "payload_size", nullable = false)
    private int payloadSize;

    @Column(nullable = false)
    private String payload;

    protected ReceivedMessageEntity() {
    }
}
