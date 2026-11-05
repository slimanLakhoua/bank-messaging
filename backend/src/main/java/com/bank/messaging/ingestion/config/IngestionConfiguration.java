package com.bank.messaging.ingestion.config;

import com.bank.messaging.ingestion.messaging.JmsMessageListener;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.ConnectionFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionConfiguration.class);

    @Bean
    DefaultMessageListenerContainer ingestionContainer(
            ConnectionFactory connectionFactory,
            JmsMessageListener listener,
            MeterRegistry metrics,
            IngestionProperties properties,
            HikariDataSource dataSource) {
        if (properties.maxConcurrency() >= dataSource.getMaximumPoolSize()) {
            throw new IllegalArgumentException("SQL pool must reserve at least one connection beyond maximum JMS concurrency");
        }
        var container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setDestinationName(properties.queue());
        container.setMessageListener(listener);
        container.setSessionTransacted(true);
        container.setConcurrentConsumers(properties.concurrency());
        container.setMaxConcurrentConsumers(properties.maxConcurrency());
        container.setAcceptMessagesWhileStopping(false);
        container.setAutoStartup(properties.autoStartup());
        container.setReceiveTimeout(1000);
        container.setRecoveryInterval(properties.retryDelayMs());
        container.setErrorHandler(error -> handleListenerError(error, metrics, properties.retryDelayMs()));
        return container;
    }

    private void handleListenerError(Throwable error, MeterRegistry metrics, long retryDelayMs) {
        metrics.counter("bank.ingestion.failures").increment();
        // Exception messages and stack traces may contain message payloads.
        LOGGER.warn("Ingestion rolled back ({})", error.getClass().getSimpleName());
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
