package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.AuditLogRepository;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryAuditLogRepository implements AuditLogRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public AuditEntry save(AuditEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public List<AuditEntry> findLatest(int limit) {
        return entries.stream()
                .sorted(Comparator.comparing(AuditEntry::occurredAt).reversed())
                .limit(limit)
                .toList();
    }
}
