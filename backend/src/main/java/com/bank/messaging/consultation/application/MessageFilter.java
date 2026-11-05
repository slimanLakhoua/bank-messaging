package com.bank.messaging.consultation.application;

import java.time.Instant;

/** Exact metadata filters; the upper date bound is exclusive. */
public record MessageFilter(String messageId, String sourceQueue, Instant from, Instant to, boolean ascending) {
    public static final MessageFilter ALL = new MessageFilter(null, null, null, null, false);

    public MessageFilter {
        validateText(messageId);
        validateText(sourceQueue);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidMessageQueryException("La date de début doit précéder la date de fin.");
        }
    }

    private static void validateText(String value) {
        if (value != null && (value.isBlank() || value.length() > 512 || value.indexOf('\0') >= 0)) {
            throw new InvalidMessageQueryException("Un filtre textuel doit contenir entre 1 et 512 caractères valides.");
        }
    }
}
