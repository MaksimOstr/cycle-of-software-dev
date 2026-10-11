package com.cycleofsoftwaredev.identity.infrastructure;

import com.cycleofsoftwaredev.identity.domain.PasswordResetToken;
import com.cycleofsoftwaredev.identity.domain.PasswordResetTokenRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
public class InMemoryPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final Map<String, PasswordResetToken> tokens = new ConcurrentHashMap<>();

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        tokens.put(token.value(), token);
        return token;
    }

    @Override
    public Optional<PasswordResetToken> findByValue(String value) {
        return Optional.ofNullable(tokens.get(value));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        tokens.values().removeIf(token -> token.userId().equals(userId));
    }
}
