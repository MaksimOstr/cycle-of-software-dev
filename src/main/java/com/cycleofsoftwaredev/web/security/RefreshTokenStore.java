package com.cycleofsoftwaredev.web.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Storage of valid refresh tokens. A token that is not here cannot be used: this is how logout works. */
public interface RefreshTokenStore {

    void save(String refreshToken, UUID userId, Duration timeToLive);

    /** Returns the owner of the token and removes the token, so that it works only once. */
    Optional<UUID> consume(String refreshToken);
}
