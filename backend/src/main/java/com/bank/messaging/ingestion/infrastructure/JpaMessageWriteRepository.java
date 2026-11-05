package com.bank.messaging.ingestion.infrastructure;

import com.bank.messaging.ingestion.application.MessageWriteRepository;
import com.bank.messaging.ingestion.application.ReceivedTextMessage;
import com.bank.messaging.persistence.ReceivedMessageJpaRepository;
import org.springframework.stereotype.Repository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.nio.charset.StandardCharsets;

@Repository
public class JpaMessageWriteRepository implements MessageWriteRepository {
    private final ReceivedMessageJpaRepository messages;

    private final Timer duration;

    public JpaMessageWriteRepository(ReceivedMessageJpaRepository messages, MeterRegistry metrics) {
        this.messages = messages;
        this.duration = metrics.timer("bank.storage.duration");
    }

    @Override
    public boolean insert(ReceivedTextMessage message) {
        return duration.record(() -> messages.insertIfAbsent(
                message.sourceQueue(),
                message.messageId(),
                message.correlationId(),
                message.payload().getBytes(StandardCharsets.UTF_8).length,
                message.payload()) == 1);
    }
}
