package com.cycleofsoftwaredev.identity.infrastructure;

import com.cycleofsoftwaredev.identity.domain.PasswordResetToken;
import com.cycleofsoftwaredev.identity.domain.PasswordResetTokenRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final Map<String, PasswordResetToken> tokens = new ConcurrentHashMap<>();

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        tokens.put(token.value(), token);
        return token;
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return Optional.ofNullable(tokens.get(token));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        tokens.values().removeIf(token -> token.userId().equals(userId));
    }
}
