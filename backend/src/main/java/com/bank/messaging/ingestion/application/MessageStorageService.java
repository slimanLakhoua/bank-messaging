package com.bank.messaging.ingestion.application;

/** Application logic; the composition root supplies the transaction boundary. */
public final class MessageStorageService implements StoreMessageUseCase {
    private final MessageWriteRepository repository;

    public MessageStorageService(MessageWriteRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean store(ReceivedTextMessage message) {
        return repository.insert(message);
    }
}
