package com.cycleofsoftwaredev.identity.api;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** An administrator blocked or unblocked a user or changed the role; recorded in the audit log. */
public record UserAccountChanged(UUID userId, String email, String change, UUID changedBy, Instant occurredAt)
        implements DomainEvent {
}
