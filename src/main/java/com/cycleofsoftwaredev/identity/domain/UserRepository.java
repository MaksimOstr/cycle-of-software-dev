package com.cycleofsoftwaredev.identity.domain;

import java.util.Optional;
import java.util.UUID;

/** Storage abstraction for users (Dependency Inversion: services do not know the database). */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String normalizedEmail);
}
