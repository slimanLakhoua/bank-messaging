package com.bank.messaging.config;

import com.bank.messaging.consultation.application.*;
import com.bank.messaging.consultation.infrastructure.TransactionalMessageQuery;
import com.bank.messaging.ingestion.application.*;
import com.bank.messaging.ingestion.infrastructure.TransactionalMessageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UseCaseConfigurationTest {
    @Test
    void onlyDecoratedPortsAreExposedAndQueryUsesOneReadOnlySnapshot() {
        var events = new ArrayList<String>();
        var manager = new RecordingTransactionManager(events);
        var repository = new MessageQueryRepository() {
            @Override public long count(MessageFilter filter) {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                        .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                events.add("count");
                return 0;
            }
            @Override public List<MessageSummary> findPage(int page, int size, MessageFilter filter) {
                events.add("page");
                return List.of();
            }
            @Override public Optional<MessageDetails> findById(long id) {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                events.add("detail");
                return Optional.empty();
            }
        };
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PlatformTransactionManager.class, () -> manager);
            context.registerBean(MessageWriteRepository.class, () -> message -> true);
            context.registerBean(MessageQueryRepository.class, () -> repository);
            context.register(UseCaseConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(MessageStorageService.class)).isEmpty();
            assertThat(context.getBeansOfType(MessageQueryService.class)).isEmpty();
            assertThat(context.getBean(StoreMessageUseCase.class)).isInstanceOf(TransactionalMessageStorage.class);
            var query = context.getBean(QueryMessagesUseCase.class);
            assertThat(query).isInstanceOf(TransactionalMessageQuery.class);

            assertThat(query.getPage(0, 20, MessageFilter.ALL).content()).isEmpty();
            assertThat(events).containsExactly("begin", "count", "page", "commit");
            events.clear();
            assertThatThrownBy(() -> query.getMessage(1)).isInstanceOf(MessageNotFoundException.class);
            assertThat(events).containsExactly("begin", "detail", "rollback");
        }
    }

    @SuppressWarnings("serial")
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events;

        private RecordingTransactionManager(List<String> events) {
            this.events = events;
        }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertThat(definition.isReadOnly()).isTrue();
            assertThat(definition.getTimeout()).isEqualTo(10);
            events.add("begin");
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
