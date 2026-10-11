package com.cycleofsoftwaredev.identity.domain;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A registered user. Single responsibility: keeps the information about the user (name, email, role)
 * and the state of the account. Password hashing, authentication and notifications are done by other classes.
 */
@Entity
@Table(name = "users", schema = "identity")
public class User {

    /** Requirement "Login and logout": the account is locked after 5 consecutive failed attempts for 15 minutes. */
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Id
    private UUID id;
    private String email;
    private Instant registeredAt;
    private String passwordHash;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;
    private int failedLogins;
    private Instant lockedUntil;
    private boolean blocked;

    protected User() {
        // for JPA
    }

    public User(UUID id, String email, String fullName, String passwordHash, Role role, Instant registeredAt) {
        this.id = Objects.requireNonNull(id);
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.registeredAt = Objects.requireNonNull(registeredAt);
        this.role = Objects.requireNonNull(role);
        rename(fullName);
    }

    public static String normalizeEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void rename(String newFullName) {
        if (newFullName == null || newFullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        this.fullName = newFullName.trim();
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
    }

    /** Sets a new password hash and removes the lock: the owner of the email has proved the identity. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        loginSucceeded();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void loginFailed(Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            // the previous lock has expired: start counting again
            loginSucceeded();
        }
        failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void loginSucceeded() {
        failedLogins = 0;
        lockedUntil = null;
    }

    /** Requirement "User management": a blocked user cannot log in or call the API until unblocked. */
    public void block() {
        this.blocked = true;
    }

    public void unblock() {
        this.blocked = false;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public UserView toView() {
        return new UserView(id, email, fullName, role, blocked);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String fullName() {
        return fullName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
