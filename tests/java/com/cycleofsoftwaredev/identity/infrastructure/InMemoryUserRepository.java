package com.cycleofsoftwaredev.identity.infrastructure;

import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryUserRepository implements UserRepository {

    private final Map<UUID, User> users = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        users.put(user.id(), user);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return users.values().stream().filter(user -> user.email().equals(normalizedEmail)).findFirst();
    }

    @Override
    public List<User> findAll() {
        return List.copyOf(users.values());
    }
}
