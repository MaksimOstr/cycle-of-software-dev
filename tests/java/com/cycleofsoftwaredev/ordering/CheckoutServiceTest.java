package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.ordering.api.OrderPlaced;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.application.CheckoutResult;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.ShopFixture;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Checkout with online card payment (Laboratory Work 2, Figure 6). */
class CheckoutServiceTest {

    private final ShopFixture shop = new ShopFixture();

    @Test
    void placesCardOrderReservesStockAndStartsPayment() {
        UUID hoodie = shop.variant("HOOD-M", "900", 5);
        UUID cartId = shop.cartWith(hoodie, 2);

        CheckoutResult result = shop.checkout.placeOrder(shop.order(cartId, PaymentMethod.CARD_ONLINE), "key-1");

        assertThat(result.status()).isEqualTo(OrderStatus.NEW);
        assertThat(result.total()).isEqualTo(Money.of("1880")); // 2 x 900 + Nova Poshta 80 (below free-shipping threshold)
        assertThat(result.paymentUrl()).startsWith("https://pay.test/");
        assertThat(shop.inventory.availableQuantity(hoodie)).isEqualTo(3);
        assertThat(shop.carts.getSnapshot(cartId).isEmpty()).isTrue();
        assertThat(shop.events.eventsOfType(OrderPlaced.class)).hasSize(1);
    }

    @Test
    void repeatedRequestWithSameKeyReturnsTheSameOrder() {
        UUID hoodie = shop.variant("HOOD-M", "1000", 5);
        UUID cartId = shop.cartWith(hoodie, 1);

        CheckoutResult first = shop.checkout.placeOrder(shop.order(cartId, PaymentMethod.CARD_ONLINE), "key-1");
        CheckoutResult second = shop.checkout.placeOrder(shop.order(cartId, PaymentMethod.CARD_ONLINE), "key-1");

        assertThat(second).isEqualTo(first);
        assertThat(shop.orderRepository.findAll()).hasSize(1);
        assertThat(shop.inventory.availableQuantity(hoodie)).isEqualTo(4);
    }

    @Test
    void appliesPromoCodeAndFreeShipping() {
        UUID phone = shop.variant("PHONE", "3000", 5);
        UUID cartId = shop.cartWith(phone, 1);
        shop.promoCodes.createPromoCode("SALE20", 20, shop.clock.instant().plus(Duration.ofDays(1)), 10);
        shop.carts.applyPromoCode(cartId, "SALE20");

        CheckoutResult result = shop.checkout.placeOrder(shop.order(cartId, PaymentMethod.CASH_ON_DELIVERY), "key-2");
        Order order = shop.orderQueries.getByNumber(result.orderNumber());

        assertThat(order.discount()).isEqualTo(Money.of("600"));
        assertThat(order.deliveryCost()).isEqualTo(Money.zero()); // 2400 is above the 2000 threshold
        assertThat(order.total()).isEqualTo(Money.of("2400"));
        assertThat(result.paymentUrl()).isNull();
    }

    @Test
    void emptyCartCannotBeOrdered() {
        UUID cartId = shop.carts.createCart(null).cartId();

        assertThatThrownBy(() -> shop.checkout.placeOrder(shop.order(cartId, PaymentMethod.CARD_ONLINE), "key-3"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void orderIsNotCreatedWhenStockRunsOut() {
        UUID phone = shop.variant("PHONE", "3000", 1);
        UUID firstCart = shop.cartWith(phone, 1);
        UUID secondCart = shop.cartWith(phone, 1);
        shop.checkout.placeOrder(shop.order(firstCart, PaymentMethod.CARD_ONLINE), "key-4");

        assertThatThrownBy(() -> shop.checkout.placeOrder(shop.order(secondCart, PaymentMethod.CARD_ONLINE), "key-5"))
                .isInstanceOf(OutOfStockException.class);
        assertThat(shop.orderRepository.findAll()).hasSize(1);
    }
}
