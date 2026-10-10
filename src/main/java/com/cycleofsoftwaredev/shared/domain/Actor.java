package com.cycleofsoftwaredev.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Who performs an operation: a customer, a staff member (manager or administrator) or the system itself
 * (scheduled jobs, payment webhooks). Used to record the author of order status changes and in the audit log.
 */
public record Actor(UUID userId, Kind kind) {

    public enum Kind { CUSTOMER, STAFF, SYSTEM }

    public Actor {
        Objects.requireNonNull(kind, "kind must not be null");
        if (kind != Kind.SYSTEM) {
            Objects.requireNonNull(userId, "userId is required for " + kind);
        }
    }

    public static Actor customer(UUID userId) {
        return new Actor(userId, Kind.CUSTOMER);
    }

    public static Actor staff(UUID userId) {
        return new Actor(userId, Kind.STAFF);
    }

    public static Actor system() {
        return new Actor(null, Kind.SYSTEM);
    }

    public boolean isStaff() {
        return kind == Kind.STAFF;
    }
}
