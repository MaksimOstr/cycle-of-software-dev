package com.cycleofsoftwaredev.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.cart.domain.Cart;
import com.cycleofsoftwaredev.cart.domain.CartItem;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules of the cart itself, without the catalog and the stock. */
class CartTest {

    private final UUID hoodie = UUID.randomUUID();
    private final UUID mug = UUID.randomUUID();
    private final Cart cart = new Cart(UUID.randomUUID(), null);

    @Test
    void keepsItemsInTheOrderTheyWereAdded() {
        cart.setQuantity(hoodie, 1);
        cart.setQuantity(mug, 2);
        cart.setQuantity(hoodie, 5);

        assertThat(cart.items()).containsExactly(new CartItem(hoodie, 5), new CartItem(mug, 2));
        assertThat(cart.quantityOf(hoodie)).isEqualTo(5);
    }

    @Test
    void zeroQuantityRemovesTheItem() {
        cart.setQuantity(hoodie, 1);
        cart.setQuantity(mug, 2);

        cart.setQuantity(hoodie, 0);
        cart.setQuantity(UUID.randomUUID(), 0); // removing what is not in the cart changes nothing

        assertThat(cart.items()).containsExactly(new CartItem(mug, 2));
        assertThat(cart.quantityOf(hoodie)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 100, 1000})
    void quantityOfOneItemIsFromOneToNinetyNine(int quantity) {
        assertThatThrownBy(() -> cart.setQuantity(hoodie, quantity)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.items()).isEmpty();
    }

    @Test
    void acceptsTheLargestAllowedQuantity() {
        cart.setQuantity(hoodie, CartItem.MAX_QUANTITY);

        assertThat(cart.quantityOf(hoodie)).isEqualTo(99);
    }

    @Test
    void clearingRemovesItemsAndThePromoCode() {
        cart.setQuantity(hoodie, 1);
        cart.applyPromoCode("WELCOME10");

        cart.clear();

        assertThat(cart.items()).isEmpty();
        assertThat(cart.promoCode()).isEmpty();
    }

    @Test
    void guestCartBecomesTheCartOfACustomer() {
        UUID customerId = UUID.randomUUID();
        assertThat(cart.isGuestCart()).isTrue();

        cart.assignTo(customerId);

        assertThat(cart.isGuestCart()).isFalse();
        assertThat(cart.customerId()).isEqualTo(customerId);
    }

    @Test
    void remembersWhenItWasChanged() {
        Instant now = Instant.parse("2026-10-05T10:00:00Z");

        cart.touch(now);

        assertThat(cart.updatedAt()).isEqualTo(now);
    }

    @Test
    void itemsCannotBeChangedFromOutside() {
        cart.setQuantity(hoodie, 1);

        assertThatThrownBy(() -> cart.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
