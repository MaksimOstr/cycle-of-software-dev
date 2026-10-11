package com.cycleofsoftwaredev.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One-time token from the password reset email. Requirement "Password recovery": valid for 1 hour. */
@Entity
@Table(name = "password_reset_tokens", schema = "identity")
public class PasswordResetToken {

    public static final Duration VALIDITY = Duration.ofHours(1);

    @Id
    private String token;
    private UUID userId;
    private Instant expiresAt;

    protected PasswordResetToken() {
        // for JPA
    }

    public PasswordResetToken(String value, UUID userId, Instant expiresAt) {
        this.token = Objects.requireNonNull(value);
        this.userId = Objects.requireNonNull(userId);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static PasswordResetToken issue(String value, UUID userId, Instant now) {
        return new PasswordResetToken(value, userId, now.plus(VALIDITY));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public String value() {
        return token;
    }

    public UUID userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
