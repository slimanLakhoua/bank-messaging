package com.bank.messaging.consultation.application;

public final class MessageQueryService implements QueryMessagesUseCase {
    private final MessageQueryRepository repository;

    public MessageQueryService(MessageQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public MessagePage getPage(int page, int size, MessageFilter filter) {
        validatePage(page, size);
        long totalElements = repository.count(filter);
        long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return new MessagePage(repository.findPage(page, size, filter), page, size, totalElements, totalPages);
    }

    @Override
    public MessageDetails getMessage(long id) {
        if (id < 1) {
            throw new InvalidMessageQueryException("L’identifiant doit être strictement positif.");
        }
        return repository.findById(id).orElseThrow(() -> new MessageNotFoundException(id));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw new InvalidMessageQueryException(
                    "page doit être compris entre 0 et 10000, size entre 1 et 100.");
        }
    }
}
