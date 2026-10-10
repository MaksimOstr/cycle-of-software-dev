package com.cycleofsoftwaredev.cart.infrastructure;

import com.cycleofsoftwaredev.cart.domain.Cart;
import com.cycleofsoftwaredev.cart.domain.CartRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
public class InMemoryCartRepository implements CartRepository {

    private final Map<UUID, Cart> carts = new ConcurrentHashMap<>();

    @Override
    public Cart save(Cart cart) {
        carts.put(cart.id(), cart);
        return cart;
    }

    @Override
    public Optional<Cart> findById(UUID id) {
        return Optional.ofNullable(carts.get(id));
    }
}
