package com.cycleofsoftwaredev.identity.api;

import java.util.Optional;
import java.util.UUID;

/** Public interface of the Identity module for other modules. */
public interface IdentityApi {

    Optional<UserView> findUser(UUID userId);
}
