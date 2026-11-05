package com.bank.messaging.consultation.application;

import java.util.List;

public record MessagePage(
        List<MessageSummary> content,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    public MessagePage {
        content = List.copyOf(content);
    }
}
