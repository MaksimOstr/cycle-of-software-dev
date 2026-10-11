package com.cycleofsoftwaredev.web.security;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * NFR "Protection from attacks": login and registration are limited to 20 requests per minute per IP address.
 * The counter of a client for the current minute is a Redis key that expires by itself; it is shared by all
 * instances of the application.
 */
@Component
public class AuthRateLimiter {

    private static final String KEY_PREFIX = "auth-requests:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;
    private final Clock clock;

    public AuthRateLimiter(StringRedisTemplate redis,
                           @Value("${shopsphere.security.auth-requests-per-minute:20}") int requestsPerMinute,
                           Clock clock) {
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    /** Counts the request and returns {@code false} if the client has exceeded the limit of this minute. */
    public boolean tryAcquire(String clientAddress) {
        long minute = clock.instant().getEpochSecond() / WINDOW.toSeconds();
        String key = KEY_PREFIX + clientAddress + ":" + minute;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, WINDOW.multipliedBy(2));
        }
        return count == null || count <= requestsPerMinute;
    }
}
