package com.cycleofsoftwaredev.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One-time token from the password reset email. Requirement "Password recovery": valid for 1 hour. */
public record PasswordResetToken(String value, UUID userId, Instant expiresAt) {

    public static final Duration VALIDITY = Duration.ofHours(1);

    public PasswordResetToken {
        Objects.requireNonNull(value);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(expiresAt);
    }

    public static PasswordResetToken issue(String value, UUID userId, Instant now) {
        return new PasswordResetToken(value, userId, now.plus(VALIDITY));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
