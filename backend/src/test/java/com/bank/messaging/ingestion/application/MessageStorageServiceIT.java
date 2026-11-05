package com.bank.messaging.ingestion.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "bank.ingestion.auto-startup=false")
class MessageStorageServiceIT {
    private final String sourceQueue = "DEV.STORAGE." + UUID.randomUUID();

    @Autowired
    private StoreMessageUseCase storageService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTestMessages() {
        jdbc.update("DELETE FROM messaging.received_message WHERE source_queue = ?", sourceQueue);
    }

    @Test
    void storageRemainsCommittedWhenTheCallingTransactionRollsBack() {
        var message = new ReceivedTextMessage(sourceQueue, "ID:independent-commit", null, "synthetic payload");
        var callerTransaction = new TransactionTemplate(transactionManager);

        callerTransaction.executeWithoutResult(status -> {
            assertThat(storageService.store(message)).isTrue();
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("""
                SELECT payload FROM messaging.received_message
                WHERE source_queue = ? AND jms_message_id = ?
                """, String.class, sourceQueue, message.messageId())).isEqualTo("synthetic payload");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void databaseConstraintFailureDoesNotLogTheMessagePayload(CapturedOutput output) {
        String payload = "synthetic-confidential-payload";
        var invalidMessage = new ReceivedTextMessage(null, "ID:invalid-source", null, payload);

        assertThatThrownBy(() -> storageService.store(invalidMessage))
                .isInstanceOf(DataAccessException.class);

        assertThat(output.getAll()).doesNotContain(payload);
    }
}
