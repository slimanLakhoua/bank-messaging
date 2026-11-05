package com.bank.messaging.consultation.application;

import java.util.List;
import java.util.Optional;

/** Read-side persistence port used by the consultation use case. */
public interface MessageQueryRepository {
    long count(MessageFilter filter);

    List<MessageSummary> findPage(int page, int size, MessageFilter filter);

    Optional<MessageDetails> findById(long id);
}
