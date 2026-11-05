package com.bank.messaging.ingestion.config;

import com.bank.messaging.ingestion.messaging.JmsMessageListener;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.SingleConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionConfigurationTest {
    @Test void consumersUseTheConfiguredRangeWithoutStartingBeforeContextInitialization() {
        var metrics = new SimpleMeterRegistry();
        try (var pool = new HikariDataSource()) {
            pool.setMaximumPoolSize(5);
            var properties = new IngestionProperties("DEV.INPUT", "DEV.BACKOUT", true, 1, 4, 1000, 3, 1024);
            var container = new IngestionConfiguration().ingestionContainer(new SingleConnectionFactory(),
                    new JmsMessageListener(null, metrics, properties), metrics, properties, pool);
            assertThat(container.getConcurrentConsumers()).isEqualTo(1);
            assertThat(container.getMaxConcurrentConsumers()).isEqualTo(4);
            assertThat(container.isSessionTransacted()).isTrue();
            assertThat(container.isRunning()).isFalse();
        } finally {
            metrics.close();
        }
    }

    @Test void consumersCannotExhaustTheConfiguredSqlConnectionBudget() {
        var metrics = new SimpleMeterRegistry();
        try (var pool = new HikariDataSource()) {
            pool.setMaximumPoolSize(4);
            var properties = new IngestionProperties("DEV.INPUT", "DEV.BACKOUT", true, 1, 4, 1000, 3, 1024);
            assertThatThrownBy(() -> new IngestionConfiguration().ingestionContainer(new SingleConnectionFactory(),
                    new JmsMessageListener(null, metrics, properties), metrics, properties, pool))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SQL pool");
        } finally {
            metrics.close();
        }
    }
}
