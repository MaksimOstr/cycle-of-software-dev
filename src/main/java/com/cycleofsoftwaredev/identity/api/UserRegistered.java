package com.cycleofsoftwaredev.identity.api;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record UserRegistered(UUID userId, String email, String fullName, Instant occurredAt) implements DomainEvent {
}
