package com.cycleofsoftwaredev.cart.domain;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findById(UUID id);
}
