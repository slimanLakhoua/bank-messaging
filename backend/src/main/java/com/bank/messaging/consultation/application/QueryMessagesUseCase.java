package com.bank.messaging.consultation.application;

/** Inbound port for bounded message consultation, independent of transport and persistence. */
public interface QueryMessagesUseCase {
    MessagePage getPage(int page, int size, MessageFilter filter);

    MessageDetails getMessage(long id);
}
