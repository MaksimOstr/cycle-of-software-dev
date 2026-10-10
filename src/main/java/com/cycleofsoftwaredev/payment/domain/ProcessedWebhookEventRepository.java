package com.cycleofsoftwaredev.payment.domain;

/** Remembers the ids of processed provider events, so that a repeated webhook is ignored. */
public interface ProcessedWebhookEventRepository {

    /** Returns {@code false} if the event was already processed. */
    boolean markProcessed(String eventId);
}
