package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.application.PlaceOrderCommand;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderFilter;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.support.ShopFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Requirement "Order list": filters and search of the manager's order list. */
class OrderListTest {

    private final ShopFixture shop = new ShopFixture();
    private UUID hoodie;
    private String cardOrder;
    private String cashOrder;
    private String pickupOrder;
    private Instant secondDay;

    @BeforeEach
    void placeOrders() {
        hoodie = shop.variant("HOOD-M", "1000", 20);
        cardOrder = place("petro@example.com", "+380501234567", DeliveryMethod.NOVA_POSHTA, PaymentMethod.CARD_ONLINE);
        cashOrder = place("olena@example.com", "+380 (67) 765-43-21", DeliveryMethod.COURIER, PaymentMethod.CASH_ON_DELIVERY);
        shop.clock.advance(Duration.ofDays(1));
        secondDay = shop.clock.instant();
        pickupOrder = place("petro@example.com", "+380501234567", DeliveryMethod.STORE_PICKUP, PaymentMethod.CASH_ON_DELIVERY);
    }

    @Test
    void listsAllOrdersNewestFirst() {
        assertThat(numbers(OrderFilter.all())).containsExactly(pickupOrder, cashOrder, cardOrder);
    }

    @Test
    void filtersByStatusPaymentAndDeliveryMethod() {
        Order order = shop.orderQueries.getByNumber(cashOrder);
        shop.orders.changeStatus(order.id(), OrderStatus.PROCESSING, Actor.staff(UUID.randomUUID()), null);

        assertThat(numbers(filter(OrderStatus.PROCESSING, null, null))).containsExactly(cashOrder);
        assertThat(numbers(filter(OrderStatus.NEW, null, null))).containsExactly(pickupOrder, cardOrder);
        assertThat(numbers(filter(null, PaymentMethod.CARD_ONLINE, null))).containsExactly(cardOrder);
        assertThat(numbers(filter(null, null, DeliveryMethod.STORE_PICKUP))).containsExactly(pickupOrder);
        assertThat(numbers(filter(OrderStatus.NEW, PaymentMethod.CASH_ON_DELIVERY, DeliveryMethod.COURIER))).isEmpty();
    }

    @Test
    void returnsOnlyTheRequestedPage() {
        assertThat(shop.orderQueries.findOrders(OrderFilter.all(), 1, 2)).extracting(Order::number)
                .containsExactly(pickupOrder, cashOrder);
        assertThat(shop.orderQueries.findOrders(OrderFilter.all(), 2, 2)).extracting(Order::number)
                .containsExactly(cardOrder);
        assertThatThrownBy(() -> shop.orderQueries.findOrders(OrderFilter.all(), 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shop.orderQueries.findOrders(OrderFilter.all(), 1, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersByDateRange() {
        assertThat(numbers(new OrderFilter(null, null, null, secondDay, null, null))).containsExactly(pickupOrder);
        assertThat(numbers(new OrderFilter(null, null, null, null, secondDay, null))).containsExactly(cashOrder, cardOrder);
    }

    @Test
    void searchesByOrderNumberEmailOrPhone() {
        assertThat(numbers(search(cardOrder.toLowerCase()))).containsExactly(cardOrder);
        assertThat(numbers(search("OLENA@"))).containsExactly(cashOrder);
        assertThat(numbers(search("067 765"))).containsExactly(cashOrder);
        assertThat(numbers(search("petro"))).containsExactly(pickupOrder, cardOrder);
        assertThat(numbers(search("nobody"))).isEmpty();
        assertThat(numbers(search("  "))).hasSize(3);
    }

    private String place(String email, String phone, DeliveryMethod delivery, PaymentMethod payment) {
        PlaceOrderCommand command = new PlaceOrderCommand(shop.cartWith(hoodie, 1), shop.customerId,
                new ContactInfo("Customer", email, phone), delivery, "Kyiv, Khreshchatyk 1", payment);
        return shop.checkout.placeOrder(command, UUID.randomUUID().toString()).orderNumber();
    }

    private List<String> numbers(OrderFilter filter) {
        return shop.orderQueries.findOrders(filter, 1, 50).stream().map(Order::number).toList();
    }

    private static OrderFilter filter(OrderStatus status, PaymentMethod payment, DeliveryMethod delivery) {
        return new OrderFilter(status, payment, delivery, null, null, null);
    }

    private static OrderFilter search(String text) {
        return new OrderFilter(null, null, null, null, null, text);
    }
}
