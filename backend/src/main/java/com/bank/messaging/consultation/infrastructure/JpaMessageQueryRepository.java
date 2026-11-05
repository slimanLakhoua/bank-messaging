package com.bank.messaging.consultation.infrastructure;

import com.bank.messaging.consultation.application.MessageDetails;
import com.bank.messaging.consultation.application.MessageFilter;
import com.bank.messaging.consultation.application.MessageQueryRepository;
import com.bank.messaging.consultation.application.MessageSummary;
import com.bank.messaging.persistence.ReceivedMessageEntity;
import com.bank.messaging.persistence.ReceivedMessageJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaMessageQueryRepository implements MessageQueryRepository {
    private final ReceivedMessageJpaRepository messages;
    private final EntityManager entityManager;

    public JpaMessageQueryRepository(ReceivedMessageJpaRepository messages, EntityManager entityManager) {
        this.messages = messages;
        this.entityManager = entityManager;
    }

    @Override
    public long count(MessageFilter filter) {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(Long.class);
        var message = query.from(ReceivedMessageEntity.class);
        query.select(builder.count(message)).where(predicates(builder, message, filter));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public List<MessageSummary> findPage(int page, int size, MessageFilter filter) {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(MessageSummary.class);
        var message = query.from(ReceivedMessageEntity.class);
        query.select(builder.construct(MessageSummary.class,
                message.get("id"), message.get("sourceQueue"), message.get("jmsMessageId"),
                message.get("correlationId"), message.get("receivedAt"), message.get("contentType"), message.get("payloadSize")))
                .where(predicates(builder, message, filter));
        query.orderBy(filter.ascending() ? builder.asc(message.get("receivedAt")) : builder.desc(message.get("receivedAt")),
                filter.ascending() ? builder.asc(message.get("id")) : builder.desc(message.get("id")));
        return entityManager.createQuery(query).setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    private Predicate[] predicates(CriteriaBuilder builder, Root<ReceivedMessageEntity> message, MessageFilter filter) {
        var predicates = new ArrayList<Predicate>();
        if (filter.messageId() != null) predicates.add(builder.equal(message.get("jmsMessageId"), filter.messageId()));
        if (filter.sourceQueue() != null) predicates.add(builder.equal(message.get("sourceQueue"), filter.sourceQueue()));
        if (filter.from() != null) predicates.add(builder.greaterThanOrEqualTo(message.get("receivedAt"), filter.from()));
        if (filter.to() != null) predicates.add(builder.lessThan(message.get("receivedAt"), filter.to()));
        return predicates.toArray(Predicate[]::new);
    }

    @Override
    public Optional<MessageDetails> findById(long id) {
        return messages.findDetailsById(id);
    }
}
