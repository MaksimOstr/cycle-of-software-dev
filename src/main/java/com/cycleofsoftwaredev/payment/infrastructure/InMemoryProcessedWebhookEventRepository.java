package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.domain.ProcessedWebhookEventRepository;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProcessedWebhookEventRepository implements ProcessedWebhookEventRepository {

    private final Set<String> eventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markProcessed(String eventId) {
        return eventIds.add(eventId);
    }
}
