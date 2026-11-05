package com.bank.messaging.ingestion.messaging;

import com.bank.messaging.ingestion.application.StoreMessageUseCase;
import com.bank.messaging.ingestion.application.ReceivedTextMessage;
import com.bank.messaging.ingestion.config.IngestionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;

@Component
public class JmsMessageListener implements SessionAwareMessageListener<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JmsMessageListener.class);
    private final StoreMessageUseCase storageService;
    private final MeterRegistry metrics;
    private final IngestionProperties properties;

    public JmsMessageListener(
            StoreMessageUseCase storageService,
            MeterRegistry metrics,
            IngestionProperties properties) {
        this.storageService = storageService;
        this.metrics = metrics;
        this.properties = properties;
    }

    @Override
    public void onMessage(Message message, Session session) throws JMSException {
        var sample = Timer.start(metrics);
        try {
            process(message, session);
        } finally {
            sample.stop(metrics.timer("bank.ingestion.duration"));
        }
    }

    private void process(Message message, Session session) throws JMSException {
        metrics.counter("bank.ingestion.received").increment();
        String messageId = message.getJMSMessageID();
        String payload = null;
        String rejectionReason;
        try {
            payload = message instanceof TextMessage textMessage ? textMessage.getText() : null;
            rejectionReason = rejectionReason(message, messageId, payload);
        } catch (JMSException failure) {
            if (!isPermanentConversionFailure(failure)) {
                throw failure;
            }
            rejectionReason = "MESSAGE_DECODING_FAILED";
        }

        if (rejectionReason != null) {
            rejectOrRetry(message, session, messageId, rejectionReason);
            return;
        }

        var receivedMessage = new ReceivedTextMessage(
                properties.queue(), messageId, message.getJMSCorrelationID(), payload);
        boolean inserted = storageService.store(receivedMessage);
        metrics.counter(inserted ? "bank.ingestion.stored" : "bank.ingestion.duplicates").increment();
    }

    private void rejectOrRetry(
            Message message,
            Session session,
            String messageId,
            String rejectionReason) throws JMSException {
        int deliveryCount = message.propertyExists("JMSXDeliveryCount")
                ? message.getIntProperty("JMSXDeliveryCount")
                : 1;
        if (deliveryCount < properties.maxAttempts()) {
            throw new IllegalArgumentException(rejectionReason);
        }
        moveToBackout(message, session, messageId, deliveryCount, rejectionReason);
        metrics.counter("bank.ingestion.backout.attempts").increment();
        LOGGER.warn("Backout send attempted source={} messageId={} reason={} deliveries={}",
                properties.queue(), messageId, rejectionReason, deliveryCount);
    }

    private String rejectionReason(Message message, String messageId, String payload) {
        if (messageId == null || messageId.isBlank()) {
            return "MISSING_MESSAGE_ID";
        }
        if (!(message instanceof TextMessage)) {
            return "UNSUPPORTED_MESSAGE_TYPE";
        }
        if (payload == null) {
            return "NULL_TEXT";
        }
        if (payload.indexOf('\0') >= 0) {
            return "TEXT_CONTAINS_NUL";
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length > properties.maxPayloadBytes()) {
            return "PAYLOAD_TOO_LARGE";
        }
        return null;
    }

    private boolean isPermanentConversionFailure(JMSException failure) {
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!visited.add(cause)) {
                continue;
            }
            if (cause instanceof java.nio.charset.CharacterCodingException
                    || cause instanceof jakarta.jms.MessageFormatException) {
                return true;
            }
            if (cause.getCause() != null) {
                pending.add(cause.getCause());
            }
            if (cause instanceof JMSException jmsFailure && jmsFailure.getLinkedException() != null) {
                pending.add(jmsFailure.getLinkedException());
            }
        }
        return false;
    }

    private void moveToBackout(
            Message message,
            Session session,
            String messageId,
            int deliveryCount,
            String rejectionReason) throws JMSException {
        var applicationProperties = new HashMap<String, Object>();
        var names = message.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            if (!name.startsWith("JMS")) {
                applicationProperties.put(name, message.getObjectProperty(name));
            }
        }

        message.clearProperties();
        for (var property : applicationProperties.entrySet()) {
            message.setObjectProperty(property.getKey(), property.getValue());
        }
        if (messageId != null) {
            message.setStringProperty("OriginalMessageId", messageId);
        }
        message.setStringProperty("OriginalSourceQueue", properties.queue());
        message.setStringProperty("FailureReason", rejectionReason);
        message.setIntProperty("FailedDeliveryCount", deliveryCount);

        try (var producer = session.createProducer(session.createQueue(properties.backoutQueue()))) {
            producer.send(message, DeliveryMode.PERSISTENT, message.getJMSPriority(), 0);
        }
    }
}
