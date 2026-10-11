package com.cycleofsoftwaredev.administration.application;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.AuditLogRepository;
import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use case "Audit log": every domain event published by any module is recorded. */
@Service
@Transactional
public class AuditLogService {

    public static final int MAX_ENTRIES = 500;

    private final AuditLogRepository auditLog;

    public AuditLogService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    public void record(DomainEvent event) {
        auditLog.save(new AuditEntry(UUID.randomUUID(), event.getClass().getSimpleName(), event.toString(), event.occurredAt()));
    }

    /** The newest records; the number is limited, because the log grows with every action. */
    public List<AuditEntry> latestEntries(int limit) {
        if (limit < 1 || limit > MAX_ENTRIES) {
            throw new IllegalArgumentException("The number of records must be between 1 and " + MAX_ENTRIES);
        }
        return auditLog.findLatest(limit);
    }
}
