package com.bank.messaging.consultation.infrastructure;

import com.bank.messaging.consultation.application.MessageDetails;
import com.bank.messaging.consultation.application.MessageFilter;
import com.bank.messaging.consultation.application.MessagePage;
import com.bank.messaging.consultation.application.QueryMessagesUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Keeps count and page in one database snapshot without introducing Spring into application logic. */
public final class TransactionalMessageQuery implements QueryMessagesUseCase {
    private final QueryMessagesUseCase delegate;
    private final TransactionTemplate pageTransaction;
    private final TransactionTemplate detailTransaction;

    public TransactionalMessageQuery(QueryMessagesUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.pageTransaction = readTransaction(transactionManager);
        this.pageTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.detailTransaction = readTransaction(transactionManager);
    }

    @Override
    public MessagePage getPage(int page, int size, MessageFilter filter) {
        return pageTransaction.execute(status -> delegate.getPage(page, size, filter));
    }

    @Override
    public MessageDetails getMessage(long id) {
        return detailTransaction.execute(status -> delegate.getMessage(id));
    }

    private static TransactionTemplate readTransaction(PlatformTransactionManager manager) {
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setTimeout(10);
        return transaction;
    }
}
