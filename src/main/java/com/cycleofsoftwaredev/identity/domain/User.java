package com.cycleofsoftwaredev.identity.domain;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A registered user. Single responsibility: keeps the information about the user (name, email, role).
 * Password hashing, authentication and notifications are done by other classes.
 */
public class User {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final Instant registeredAt;
    private String fullName;
    private Role role;

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

    public UserView toView() {
        return new UserView(id, email, fullName, role);
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
