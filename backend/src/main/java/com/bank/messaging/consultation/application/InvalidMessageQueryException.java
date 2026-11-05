package com.bank.messaging.consultation.application;

public class InvalidMessageQueryException extends RuntimeException {
    public InvalidMessageQueryException(String message) {
        super(message);
    }
}
