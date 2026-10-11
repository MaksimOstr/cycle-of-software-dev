package com.cycleofsoftwaredev.cart.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findById(UUID id);

    /** Removes the carts of guests that were not changed since the given time; returns how many were removed. */
    int deleteGuestCartsNotChangedSince(Instant time);
}
