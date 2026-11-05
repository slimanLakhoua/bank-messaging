package com.bank.messaging.ingestion.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionPropertiesTest {
    @Test
    void maximumConcurrencyCannotBeBelowMinimum() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("bank.ingestion.queue", "DEV.INPUT", "bank.ingestion.backout-queue", "DEV.BACKOUT",
                        "bank.ingestion.concurrency", "4", "bank.ingestion.max-concurrency", "2",
                        "bank.ingestion.retry-delay-ms", "1000", "bank.ingestion.max-attempts", "3",
                        "bank.ingestion.max-payload-bytes", "1024"));
        var binder = new org.springframework.boot.context.properties.bind.Binder(source);
        assertThatThrownBy(() -> binder.bind("bank.ingestion", IngestionProperties.class))
                .isInstanceOf(org.springframework.boot.context.properties.bind.BindException.class);
    }

    @Test
    void sourceAndBackoutQueuesMustBeDifferent() {
        assertThatThrownBy(() -> new IngestionProperties("DEV.INPUT", "DEV.INPUT", true, 1, 1, 1000, 3, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue");
    }

    @Test
    void numericSettingsMustBePositive() {
        assertThatThrownBy(() -> new IngestionProperties("DEV.INPUT", "DEV.BACKOUT", true, 0, 1, 1000, 3, 1024))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
