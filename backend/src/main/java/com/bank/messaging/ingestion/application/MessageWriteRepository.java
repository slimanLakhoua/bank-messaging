package com.bank.messaging.ingestion.application;

/** Write-side persistence port used by message ingestion. */
public interface MessageWriteRepository {
    boolean insert(ReceivedTextMessage message);
}
