package com.cycleofsoftwaredev.identity.domain;

/** Hashing of passwords. Passwords are never stored in plain text (NFR "Password storage"). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);
}
