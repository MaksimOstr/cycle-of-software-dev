package com.cycleofsoftwaredev.administration.domain;

import java.time.Instant;
import java.util.UUID;

/** One record of the audit log: what happened and when. */
public record AuditEntry(UUID id, String eventType, String details, Instant occurredAt) {
}
