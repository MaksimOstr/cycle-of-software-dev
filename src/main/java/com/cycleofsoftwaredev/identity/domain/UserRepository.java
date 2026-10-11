package com.cycleofsoftwaredev.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage abstraction for users (Dependency Inversion: services do not know the database). */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String normalizedEmail);

    /**
     * Users whose email or name contains the lower-case text (all users for an empty text), ordered by email,
     * at most {@code limit} of them.
     */
    List<User> search(String text, int limit);
}
