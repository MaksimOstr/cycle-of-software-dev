package com.cycleofsoftwaredev.administration.application;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.AuditLogRepository;
import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Use case "Audit log": every domain event published by any module is recorded. */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLog;

    public AuditLogService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    public void record(DomainEvent event) {
        auditLog.append(new AuditEntry(UUID.randomUUID(), event.getClass().getSimpleName(), event.toString(), event.occurredAt()));
    }

    public List<AuditEntry> latestEntries(int limit) {
        return auditLog.findAll().stream()
                .sorted(Comparator.comparing(AuditEntry::occurredAt).reversed())
                .limit(limit)
                .toList();
    }
}
