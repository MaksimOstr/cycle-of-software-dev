package com.cycleofsoftwaredev.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.cart.application.CartService;
import com.cycleofsoftwaredev.cart.domain.Cart;
import com.cycleofsoftwaredev.cart.domain.CartItem;
import com.cycleofsoftwaredev.cart.domain.CartRepository;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Carts in the real PostgreSQL started by Testcontainers. */
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class CartPersistenceTest {

    @Autowired
    private CartRepository carts;

    @Autowired
    private CartService cartService;

    @Test
    void savedCartIsReadBackWithItemsInTheOrderTheyWereAdded() {
        UUID customerId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Cart cart = new Cart(UUID.randomUUID(), customerId);
        cart.touch(Instant.now());
        cart.setQuantity(first, 2);
        cart.setQuantity(second, 5);
        cart.setQuantity(third, 1);
        cart.applyPromoCode("WELCOME10");
        carts.save(cart);

        Cart loaded = carts.findById(cart.id()).orElseThrow();
        loaded.setQuantity(first, 4);
        loaded.setQuantity(second, 0);
        carts.save(loaded);

        Cart changed = carts.findById(cart.id()).orElseThrow();
        assertThat(changed.customerId()).isEqualTo(customerId);
        assertThat(changed.promoCode()).contains("WELCOME10");
        assertThat(changed.items()).containsExactly(new CartItem(first, 4), new CartItem(third, 1));
    }

    @Test
    void onlyGuestCartsThatWereNotChangedForThirtyDaysAreRemoved() {
        Instant now = Instant.now();
        Instant longAgo = now.minus(CartService.GUEST_CART_LIFETIME).minus(Duration.ofHours(1));
        Cart abandonedGuestCart = carts.save(cart(null, longAgo));
        Cart freshGuestCart = carts.save(cart(null, now.minus(Duration.ofDays(29))));
        Cart oldCustomerCart = carts.save(cart(UUID.randomUUID(), longAgo));

        int removed = cartService.removeAbandonedGuestCarts();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(carts.findById(abandonedGuestCart.id())).isEmpty();
        assertThat(carts.findById(freshGuestCart.id())).isPresent();
        assertThat(carts.findById(oldCustomerCart.id())).isPresent();
    }

    @Test
    void unknownCartIsNotFound() {
        assertThat(carts.findById(UUID.randomUUID())).isEmpty();
    }

    private static Cart cart(UUID customerId, Instant changedAt) {
        Cart cart = new Cart(UUID.randomUUID(), customerId);
        cart.setQuantity(UUID.randomUUID(), 1);
        cart.touch(changedAt);
        return cart;
    }
}
