package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import java.time.Instant;
import java.util.UUID;

/** Record of the audit log as returned to the administrator. */
public record AuditEntryResponse(UUID id, String eventType, String details, Instant occurredAt) {

    static AuditEntryResponse of(AuditEntry entry) {
        return new AuditEntryResponse(entry.id(), entry.eventType(), entry.details(), entry.occurredAt());
    }
}
