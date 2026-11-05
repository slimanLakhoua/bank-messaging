package com.bank.messaging.ingestion.application;

public record ReceivedTextMessage(
        String sourceQueue,
        String messageId,
        String correlationId,
        String payload) {
}
