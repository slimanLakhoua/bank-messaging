package com.bank.messaging.consultation.application;

import java.time.Instant;

public record MessageSummary(
        long id,
        String sourceQueue,
        String jmsMessageId,
        String correlationId,
        Instant receivedAt,
        String contentType,
        int payloadSize) {
}
