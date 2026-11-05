package com.bank.messaging.config;

import com.bank.messaging.consultation.application.MessageQueryRepository;
import com.bank.messaging.consultation.application.MessageQueryService;
import com.bank.messaging.consultation.application.QueryMessagesUseCase;
import com.bank.messaging.consultation.infrastructure.TransactionalMessageQuery;
import com.bank.messaging.ingestion.application.MessageStorageService;
import com.bank.messaging.ingestion.application.MessageWriteRepository;
import com.bank.messaging.ingestion.application.StoreMessageUseCase;
import com.bank.messaging.ingestion.infrastructure.TransactionalMessageStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Composition root: only the decorated inbound ports are registered as application beans. */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    StoreMessageUseCase storeMessageUseCase(MessageWriteRepository repository, PlatformTransactionManager manager) {
        return new TransactionalMessageStorage(new MessageStorageService(repository), manager);
    }

    @Bean
    QueryMessagesUseCase queryMessagesUseCase(MessageQueryRepository repository, PlatformTransactionManager manager) {
        return new TransactionalMessageQuery(new MessageQueryService(repository), manager);
    }
}
