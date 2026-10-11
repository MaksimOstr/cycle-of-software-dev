package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.domain.ProcessedWebhookEventRepository;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryProcessedWebhookEventRepository implements ProcessedWebhookEventRepository {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markProcessed(String eventId) {
        return processed.add(eventId);
    }
}
