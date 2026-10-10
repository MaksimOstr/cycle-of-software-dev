package com.cycleofsoftwaredev.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.cart.api.CartSnapshot;
import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.support.ShopFixture;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartServiceTest {

    private final ShopFixture shop = new ShopFixture();

    @Test
    void addsItemsWithCurrentCatalogPrice() {
        UUID hoodie = shop.variant("HOOD-M", "1299.00", 10);
        UUID cartId = shop.carts.createCart(null).cartId();

        shop.carts.addItem(cartId, hoodie, 1);
        CartSnapshot cart = shop.carts.addItem(cartId, hoodie, 2);

        assertThat(cart.lines()).singleElement().satisfies(line -> {
            assertThat(line.quantity()).isEqualTo(3);
            assertThat(line.unitPrice()).isEqualTo(Money.of("1299.00"));
        });
        assertThat(cart.subtotal()).isEqualTo(Money.of("3897.00"));
    }

    @Test
    void cannotAddMoreThanAvailable() {
        UUID phone = shop.variant("PHONE", "30000", 2);
        UUID cartId = shop.carts.createCart(null).cartId();

        assertThatThrownBy(() -> shop.carts.addItem(cartId, phone, 3)).isInstanceOf(OutOfStockException.class);
        assertThatThrownBy(() -> shop.carts.addItem(cartId, UUID.randomUUID(), 1)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesAndRemovesItems() {
        UUID hoodie = shop.variant("HOOD-M", "1000", 10);
        UUID cartId = shop.cartWith(hoodie, 1);

        assertThat(shop.carts.updateQuantity(cartId, hoodie, 4).subtotal()).isEqualTo(Money.of("4000"));
        assertThat(shop.carts.removeItem(cartId, hoodie).isEmpty()).isTrue();
    }

    @Test
    void appliesValidPromoCode() {
        UUID hoodie = shop.variant("HOOD-M", "1000", 10);
        UUID cartId = shop.cartWith(hoodie, 2);
        shop.promoCodes.createPromoCode("welcome10", 10, shop.clock.instant().plus(Duration.ofDays(1)), 100);

        CartSnapshot cart = shop.carts.applyPromoCode(cartId, "WELCOME10");

        assertThat(cart.promoCode()).isEqualTo("WELCOME10");
        assertThat(cart.discount()).isEqualTo(Money.of("200"));
        assertThat(cart.totalAfterDiscount()).isEqualTo(Money.of("1800"));
    }

    @Test
    void rejectsExpiredPromoCode() {
        UUID hoodie = shop.variant("HOOD-M", "1000", 10);
        UUID cartId = shop.cartWith(hoodie, 1);
        shop.promoCodes.createPromoCode("OLD", 10, shop.clock.instant().plus(Duration.ofHours(1)), 100);
        shop.clock.advance(Duration.ofHours(2));

        assertThatThrownBy(() -> shop.carts.applyPromoCode(cartId, "OLD")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void completedCheckoutEmptiesCart() {
        UUID hoodie = shop.variant("HOOD-M", "1000", 10);
        UUID cartId = shop.cartWith(hoodie, 1);

        shop.carts.completeCheckout(cartId, UUID.randomUUID());

        assertThat(shop.carts.getSnapshot(cartId).isEmpty()).isTrue();
    }
}
