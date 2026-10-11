package com.cycleofsoftwaredev.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.cycleofsoftwaredev.support.MutableClock;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import com.cycleofsoftwaredev.web.security.AuthRateLimiter;
import com.cycleofsoftwaredev.web.security.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Refresh tokens and the limit of login requests in the real Redis started by Testcontainers: what is
 * stored, that it expires by itself and that a token works once even for simultaneous requests.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class RedisSecurityStorageTest {

    @Autowired
    private RefreshTokenStore refreshTokens;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void refreshTokenGivesItsOwnerOnlyOnce() {
        UUID userId = UUID.randomUUID();
        String token = newToken();
        refreshTokens.save(token, userId, Duration.ofDays(7));

        assertThat(refreshTokens.consume(token)).contains(userId);
        assertThat(refreshTokens.consume(token)).isEmpty();
        assertThat(refreshTokens.consume(newToken())).isEmpty();
    }

    /** NFR "Secrets management": whoever reads Redis cannot sign in with what is stored there. */
    @Test
    void redisKeepsOnlyTheHashOfTheTokenWithItsLifetime() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = newToken();
        refreshTokens.save(token, userId, Duration.ofDays(7));

        assertThat(redis.keys("refresh-token:*")).isNotEmpty().noneMatch(key -> key.contains(token));
        assertThat(redis.opsForValue().get(keyOf(token))).isEqualTo(userId.toString());
        assertThat(redis.getExpire(keyOf(token), TimeUnit.SECONDS))
                .isBetween(Duration.ofDays(7).toSeconds() - 60, Duration.ofDays(7).toSeconds());
    }

    @Test
    void expiredRefreshTokenIsRemovedByRedisItself() throws Exception {
        String token = newToken();
        String key = keyOf(token);
        refreshTokens.save(token, UUID.randomUUID(), Duration.ofSeconds(1));
        assertThat(redis.hasKey(key)).isTrue();

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).until(() -> !redis.hasKey(key));

        assertThat(refreshTokens.consume(token)).isEmpty();
    }

    /** A stolen and a legitimate copy of one token used at the same moment: only one request gets new tokens. */
    @Test
    void simultaneousRequestsWithOneRefreshTokenAreServedOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = newToken();
        refreshTokens.save(token, userId, Duration.ofMinutes(1));
        List<Callable<Optional<UUID>>> requests = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            requests.add(() -> refreshTokens.consume(token));
        }

        List<Optional<UUID>> owners = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            for (Future<Optional<UUID>> request : executor.invokeAll(requests)) {
                owners.add(request.get());
            }
        }

        assertThat(owners).filteredOn(Optional::isPresent).containsExactly(Optional.of(userId));
    }

    /** NFR "Protection from attacks": 20 login and registration requests per minute from one address. */
    @Test
    void requestsOfOneAddressAreLimitedPerMinuteAndTheCounterExpires() {
        MutableClock clock = new MutableClock(Instant.now());
        AuthRateLimiter limiter = new AuthRateLimiter(redis, 3, clock);
        String attacker = "203.0.113." + System.nanoTime();
        String customer = "198.51.100." + System.nanoTime();

        assertThat(limiter.tryAcquire(attacker)).isTrue();
        assertThat(limiter.tryAcquire(attacker)).isTrue();
        assertThat(limiter.tryAcquire(attacker)).isTrue();
        assertThat(limiter.tryAcquire(attacker)).isFalse();
        assertThat(limiter.tryAcquire(attacker)).isFalse();
        assertThat(limiter.tryAcquire(customer)).isTrue(); // other addresses are not affected

        Set<String> counters = redis.keys("auth-requests:" + attacker + ":*");
        assertThat(counters).hasSize(1);
        assertThat(redis.getExpire(counters.iterator().next(), TimeUnit.SECONDS)).isBetween(1L, 120L);

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire(attacker)).isTrue(); // a new minute starts a new counter
    }

    private static String newToken() {
        return "token-" + UUID.randomUUID();
    }

    /** The key under which the token is expected in Redis: the SHA-256 hash of the token in hexadecimal. */
    private static String keyOf(String token) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return "refresh-token:" + HexFormat.of().formatHex(hash);
    }
}
