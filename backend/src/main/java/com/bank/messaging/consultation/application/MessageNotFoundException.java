package com.bank.messaging.consultation.application;

public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(long id) {
        super("Message %d introuvable.".formatted(id));
    }
}
