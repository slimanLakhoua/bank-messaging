package com.bank.messaging.consultation.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageQueryServiceTest {
    private final StubMessageQueryRepository repository = new StubMessageQueryRepository();
    private final MessageQueryService service = new MessageQueryService(repository);

    @Test
    void pageCalculatesTheLastPartialPage() {
        repository.total = 21;
        repository.messages = List.of(summary(21));

        var result = service.getPage(1, 20, MessageFilter.ALL);

        assertThat(result.content()).containsExactly(summary(21));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void invalidPaginationIsRejectedBeforeAccessingTheRepository() {
        assertThatThrownBy(() -> service.getPage(-1, 20, MessageFilter.ALL))
                .isInstanceOf(InvalidMessageQueryException.class);

        assertThat(repository.accessCount).isZero();
    }

    @Test
    void missingMessageHasAnApplicationSpecificError() {
        assertThatThrownBy(() -> service.getMessage(42))
                .isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    void existingMessageIsReturned() {
        var expected = new MessageDetails(7, "DEV.INPUT", "ID:7", "correlation",
                Instant.parse("2026-11-04T12:00:00Z"), "text/plain;charset=UTF-8", 4, "test");
        repository.message = Optional.of(expected);

        assertThat(service.getMessage(7)).isEqualTo(expected);
    }

    private MessageSummary summary(long id) {
        return new MessageSummary(id, "DEV.INPUT", "ID:" + id, null,
                Instant.parse("2026-11-04T12:00:00Z"), "text/plain;charset=UTF-8", 4);
    }

    private static final class StubMessageQueryRepository implements MessageQueryRepository {
        private long total;
        private int accessCount;
        private List<MessageSummary> messages = List.of();
        private Optional<MessageDetails> message = Optional.empty();

        @Override
        public long count(MessageFilter filter) {
            accessCount++;
            return total;
        }

        @Override
        public List<MessageSummary> findPage(int page, int size, MessageFilter filter) {
            accessCount++;
            return messages;
        }

        @Override
        public Optional<MessageDetails> findById(long id) {
            accessCount++;
            return message;
        }
    }
}
