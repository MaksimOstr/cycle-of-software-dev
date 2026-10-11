package com.cycleofsoftwaredev.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh tokens in Redis: every token is one key that Redis removes itself when the token expires.
 * Only the SHA-256 hash of a token is stored, so the content of Redis cannot be used to sign in.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh-token:";

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String refreshToken, UUID userId, Duration timeToLive) {
        redis.opsForValue().set(key(refreshToken), userId.toString(), timeToLive);
    }

    @Override
    public Optional<UUID> consume(String refreshToken) {
        // GETDEL is atomic: of two simultaneous requests with one token only one gets the user id
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(refreshToken))).map(UUID::fromString);
    }

    private static String key(String refreshToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
