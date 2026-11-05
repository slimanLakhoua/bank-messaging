package com.bank.messaging.persistence;

import com.bank.messaging.consultation.application.MessageDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReceivedMessageJpaRepository extends JpaRepository<ReceivedMessageEntity, Long> {

    @Query("""
            select new com.bank.messaging.consultation.application.MessageDetails(
                m.id, m.sourceQueue, m.jmsMessageId, m.correlationId, m.receivedAt, m.contentType, m.payloadSize, m.payload)
            from ReceivedMessageEntity m
            where m.id = :id
            """)
    Optional<MessageDetails> findDetailsById(@Param("id") long id);

    /** Returns 1 when inserted, 0 when (source_queue, jms_message_id) already exists; a duplicate must not abort the transaction. */
    @Modifying
    @Query(value = """
            INSERT INTO messaging.received_message
                (source_queue, jms_message_id, correlation_id, content_type, payload_size, payload)
            VALUES (:sourceQueue, :messageId, :correlationId, 'text/plain;charset=UTF-8', :payloadSize, :payload)
            ON CONFLICT (source_queue, jms_message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("sourceQueue") String sourceQueue,
            @Param("messageId") String messageId,
            @Param("correlationId") String correlationId,
            @Param("payloadSize") int payloadSize,
            @Param("payload") String payload);
}
