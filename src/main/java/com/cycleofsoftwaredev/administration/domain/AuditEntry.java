package com.cycleofsoftwaredev.administration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One record of the audit log: what happened and when. */
@Entity
@Table(name = "audit_log", schema = "administration")
public class AuditEntry {

    public static final int MAX_DETAILS_LENGTH = 4000;

    @Id
    private UUID id;
    private String eventType;

    @Column(length = MAX_DETAILS_LENGTH)
    private String details;

    private Instant occurredAt;

    protected AuditEntry() {
        // for JPA
    }

    public AuditEntry(UUID id, String eventType, String details, Instant occurredAt) {
        this.id = Objects.requireNonNull(id);
        this.eventType = Objects.requireNonNull(eventType);
        this.details = details.length() > MAX_DETAILS_LENGTH ? details.substring(0, MAX_DETAILS_LENGTH) : details;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    public UUID id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public String details() {
        return details;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
