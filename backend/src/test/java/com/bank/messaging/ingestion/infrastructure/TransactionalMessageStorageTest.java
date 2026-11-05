package com.bank.messaging.ingestion.infrastructure;

import com.bank.messaging.ingestion.application.MessageStorageService;
import com.bank.messaging.ingestion.application.MessageWriteRepository;
import com.bank.messaging.ingestion.application.ReceivedTextMessage;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionalMessageStorageTest {
    private final RecordingRepository repository = new RecordingRepository();
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final TransactionalMessageStorage service = new TransactionalMessageStorage(
            new MessageStorageService(repository), transactions);

    @Test
    void returnsOnlyAfterTheInsertTransactionCommits() {
        var message = new ReceivedTextMessage("DEV.INPUT", "ID:1", "correlation", "payload");

        assertThat(service.store(message)).isTrue();

        assertThat(repository.saved).isEqualTo(message);
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
    }

    @Test
    void repositoryFailureRollsBackAndIsPropagated() {
        repository.failure = new IllegalStateException("database unavailable");

        assertThatThrownBy(() -> service.store(
                new ReceivedTextMessage("DEV.INPUT", "ID:2", null, "payload")))
                .isSameAs(repository.failure);

        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryStillCompletesTheTransaction() {
        repository.inserted = false;
        assertThat(service.store(new ReceivedTextMessage("DEV.INPUT", "ID:1", null, "payload"))).isFalse();
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    void commitFailurePropagatesToTheCaller() {
        transactions.commitFailure = new org.springframework.transaction.TransactionSystemException("commit failed");
        assertThatThrownBy(() -> service.store(new ReceivedTextMessage("DEV.INPUT", "ID:1", null, "payload")))
                .isSameAs(transactions.commitFailure);
    }

    private static final class RecordingRepository implements MessageWriteRepository {
        private ReceivedTextMessage saved;
        private RuntimeException failure;
        private boolean inserted = true;

        @Override
        public boolean insert(ReceivedTextMessage message) {
            if (failure != null) {
                throw failure;
            }
            saved = message;
            return inserted;
        }
    }

    @SuppressWarnings("serial")
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commits;
        private int rollbacks;
        private RuntimeException commitFailure;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThat(definition.getTimeout()).isEqualTo(10);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (commitFailure != null) throw commitFailure;
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
