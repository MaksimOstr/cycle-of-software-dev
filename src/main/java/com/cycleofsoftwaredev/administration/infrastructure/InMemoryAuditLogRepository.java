package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.AuditLogRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAuditLogRepository implements AuditLogRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<AuditEntry> findAll() {
        return List.copyOf(entries);
    }
}
