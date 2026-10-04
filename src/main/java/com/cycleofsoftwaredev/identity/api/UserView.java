package com.cycleofsoftwaredev.identity.api;

import java.util.UUID;

/** Public, read-only view of a user. Never contains the password hash. */
public record UserView(UUID id, String email, String fullName, Role role) {
}
