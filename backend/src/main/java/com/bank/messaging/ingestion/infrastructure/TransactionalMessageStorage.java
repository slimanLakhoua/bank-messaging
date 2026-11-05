package com.bank.messaging.ingestion.infrastructure;

import com.bank.messaging.ingestion.application.ReceivedTextMessage;
import com.bank.messaging.ingestion.application.StoreMessageUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits SQL before returning control to the JMS transaction, including when a caller has a SQL transaction. */
public final class TransactionalMessageStorage implements StoreMessageUseCase {
    private final StoreMessageUseCase delegate;
    private final TransactionTemplate transaction;

    public TransactionalMessageStorage(StoreMessageUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(10);
    }

    @Override
    public boolean store(ReceivedTextMessage message) {
        return Boolean.TRUE.equals(transaction.execute(status -> delegate.store(message)));
    }
}
