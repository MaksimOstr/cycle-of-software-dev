package com.cycleofsoftwaredev.identity.api;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** A user asked for a password reset; the Notification module sends the token by email. */
public record PasswordResetRequested(UUID userId, String email, String token, Instant expiresAt, Instant occurredAt)
        implements DomainEvent {

    /** The token is a secret: only the email contains it, the audit log record does not. */
    @Override
    public String toString() {
        return "PasswordResetRequested[userId=" + userId + ", email=" + email + ", expiresAt=" + expiresAt + "]";
    }
}
