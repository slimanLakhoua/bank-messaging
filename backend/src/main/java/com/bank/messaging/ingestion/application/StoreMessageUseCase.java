package com.bank.messaging.ingestion.application;

/** Inbound port. A successful return requires a durable store, including duplicate delivery. */
public interface StoreMessageUseCase {
    /** Returns false when the transport identity has already been stored. Failures must propagate. */
    boolean store(ReceivedTextMessage message);
}
