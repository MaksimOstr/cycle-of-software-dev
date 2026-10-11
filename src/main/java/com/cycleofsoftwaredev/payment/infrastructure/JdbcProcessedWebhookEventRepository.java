package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.domain.ProcessedWebhookEventRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Remembers processed provider events in PostgreSQL. The primary key makes the check atomic: when the same
 * event arrives twice, even at the same moment, only one INSERT adds a row.
 */
@Repository
public class JdbcProcessedWebhookEventRepository implements ProcessedWebhookEventRepository {

    private final JdbcClient jdbc;

    public JdbcProcessedWebhookEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(String eventId) {
        int inserted = jdbc.sql("""
                        INSERT INTO payment.processed_webhook_events (event_id, processed_at)
                        VALUES (:eventId, now())
                        ON CONFLICT (event_id) DO NOTHING""")
                .param("eventId", eventId)
                .update();
        return inserted == 1;
    }
}
