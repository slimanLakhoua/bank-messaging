package com.bank.messaging.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank.ingestion")
public record IngestionProperties(
        String queue,
        String backoutQueue,
        boolean autoStartup,
        int concurrency,
        int maxConcurrency,
        long retryDelayMs,
        int maxAttempts,
        int maxPayloadBytes) {

    public IngestionProperties {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("Source queue must be configured");
        }
        if (backoutQueue == null || backoutQueue.isBlank() || queue.equals(backoutQueue)) {
            throw new IllegalArgumentException("Backout queue must be configured and differ from source queue");
        }
        if (maxConcurrency < concurrency) {
            throw new IllegalArgumentException("Maximum concurrency must be at least minimum concurrency");
        }
        if (concurrency < 1 || retryDelayMs < 1 || maxAttempts < 1 || maxPayloadBytes < 1) {
            throw new IllegalArgumentException("Numeric ingestion settings must be positive");
        }
    }
}
