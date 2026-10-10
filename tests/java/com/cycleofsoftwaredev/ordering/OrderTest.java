package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.OrderPlaced;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.OrderStatusChanged;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.DeliveryInfo;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderItem;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** State machine of an order (Laboratory Work 2, Figure 8). */
class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private final UUID customerId = UUID.randomUUID();
    private final Actor manager = Actor.staff(UUID.randomUUID());

    private Order newOrder(PaymentMethod paymentMethod) {
        OrderItem item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "SKU-1", "Hoodie", Money.of("500"), 2);
        return Order.place(UUID.randomUUID(), "SS-1", UUID.randomUUID().toString(), customerId,
                new ContactInfo("Petro", "petro@example.com", "+380501234567"),
                DeliveryInfo.of(DeliveryMethod.COURIER, "Kyiv, Khreshchatyk 1"), paymentMethod, List.of(item),
                "WELCOME10", Money.of("100"), Money.of("120"), NOW);
    }

    @Test
    void newOrderCalculatesTotalsAndRecordsEvent() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.subtotal()).isEqualTo(Money.of("1000"));
        assertThat(order.total()).isEqualTo(Money.of("1020"));
        assertThat(order.paymentDeadline()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(order.pullEvents()).singleElement().isInstanceOf(OrderPlaced.class);
        assertThat(order.pullEvents()).isEmpty();
    }

    @Test
    void cardOrderGoesThroughTheWholeLifecycle() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);

        order.markPaid(NOW);
        order.startProcessing(manager, NOW);
        order.ship("20450000000001", manager, NOW);
        order.markDelivered(manager, NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.delivery().trackingNumber()).isEqualTo("20450000000001");
        assertThat(order.history()).extracting(change -> change.toStatus()).containsExactly(
                OrderStatus.NEW, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        List<DomainEvent> events = order.pullEvents();
        assertThat(events).filteredOn(OrderStatusChanged.class::isInstance).hasSize(4);
    }

    @Test
    void cashOrderIsConfirmedByManagerWithoutPayment() {
        Order order = newOrder(PaymentMethod.CASH_ON_DELIVERY);

        assertThat(order.paymentDeadline()).isNull();
        order.startProcessing(manager, NOW);

        assertThat(order.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.wasPaidOnline()).isFalse();
    }

    @Test
    void rejectsInvalidTransitions() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);

        assertThatThrownBy(() -> order.startProcessing(manager, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> order.ship("123", manager, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> order.markDelivered(manager, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThat(order.delivery().trackingNumber()).isNull();
    }

    @Test
    void onlyStaffManagesOrders() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);
        order.markPaid(NOW);

        assertThatThrownBy(() -> order.startProcessing(Actor.customer(customerId), NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotBeCancelledAfterShipment() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);
        order.markPaid(NOW);
        order.startProcessing(manager, NOW);
        order.ship("123", manager, NOW);

        assertThat(order.canBeCancelled()).isFalse();
        assertThatThrownBy(() -> order.cancel(Actor.customer(customerId), "changed my mind", NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void customerCancelsOnlyOwnOrder() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);

        assertThatThrownBy(() -> order.cancel(Actor.customer(UUID.randomUUID()), "not mine", NOW))
                .isInstanceOf(BusinessRuleException.class);
        order.cancel(Actor.customer(customerId), "changed my mind", NOW);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void returnIsAllowedWithinFourteenDaysAfterDelivery() {
        Order order = newOrder(PaymentMethod.CASH_ON_DELIVERY);
        order.startProcessing(manager, NOW);
        order.ship("123", manager, NOW);
        order.markDelivered(manager, NOW);

        assertThat(order.isReturnAllowed(NOW.plus(Duration.ofDays(13)))).isTrue();
        assertThat(order.isReturnAllowed(NOW.plus(Duration.ofDays(15)))).isFalse();
        assertThatThrownBy(() -> order.markReturned(manager, NOW.plus(Duration.ofDays(15))))
                .isInstanceOf(BusinessRuleException.class);
        order.markReturned(manager, NOW.plus(Duration.ofDays(3)));
        assertThat(order.status()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void cardOrderBecomesOverdueAfterThirtyMinutes() {
        Order order = newOrder(PaymentMethod.CARD_ONLINE);

        assertThat(order.isPaymentOverdue(NOW.plus(Duration.ofMinutes(29)))).isFalse();
        assertThat(order.isPaymentOverdue(NOW.plus(Duration.ofMinutes(30)))).isTrue();
    }

    @Test
    void orderMustHaveItemsAndValidDiscount() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "SS-2", "key", null,
                new ContactInfo("Petro", "petro@example.com", "+380501234567"),
                DeliveryInfo.of(DeliveryMethod.STORE_PICKUP, null), PaymentMethod.CARD_ONLINE, List.of(),
                null, Money.zero(), Money.zero(), NOW))
                .isInstanceOf(BusinessRuleException.class);
    }
}
