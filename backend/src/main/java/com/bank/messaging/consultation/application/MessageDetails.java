package com.bank.messaging.consultation.application;

import java.time.Instant;

public record MessageDetails(
        long id,
        String sourceQueue,
        String jmsMessageId,
        String correlationId,
        Instant receivedAt,
        String contentType,
        int payloadSize,
        String payload) {
}
