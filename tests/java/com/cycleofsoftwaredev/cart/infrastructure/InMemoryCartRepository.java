package com.cycleofsoftwaredev.cart.infrastructure;

import com.cycleofsoftwaredev.cart.domain.Cart;
import com.cycleofsoftwaredev.cart.domain.CartRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
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

    @Override
    public int deleteGuestCartsNotChangedSince(Instant time) {
        List<UUID> abandoned = carts.values().stream()
                .filter(cart -> cart.isGuestCart() && cart.updatedAt().isBefore(time))
                .map(Cart::id)
                .toList();
        abandoned.forEach(carts::remove);
        return abandoned.size();
    }
}
