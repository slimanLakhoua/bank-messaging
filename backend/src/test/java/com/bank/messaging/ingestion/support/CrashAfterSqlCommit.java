package com.bank.messaging.ingestion.support;

import com.bank.messaging.MessagingApplication;

import com.bank.messaging.ingestion.application.StoreMessageUseCase;
import com.bank.messaging.ingestion.application.ReceivedTextMessage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;


/** Test-only child JVM: terminates after SQL commit but before returning to the JMS listener. */
public class CrashAfterSqlCommit {
    public static void main(String[] args) {
        new SpringApplicationBuilder(MessagingApplication.class, CrashConfiguration.class)
                .web(WebApplicationType.NONE).run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CrashConfiguration {
        @Bean
        @Primary
        StoreMessageUseCase crashStorageService(
                @Qualifier("storeMessageUseCase") StoreMessageUseCase delegate) {
            return new StoreMessageUseCase() {
                @Override
                public boolean store(ReceivedTextMessage message) {
                    boolean saved = delegate.store(message);
                    Runtime.getRuntime().halt(23);
                    return saved;
                }
            };
        }
    }
}
