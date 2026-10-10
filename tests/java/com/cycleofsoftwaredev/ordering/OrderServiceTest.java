package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.application.CheckoutResult;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.payment.api.PaymentSession;
import com.cycleofsoftwaredev.payment.application.PaymentWebhookEvent;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.support.ShopFixture;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Handling of the payment result and order management (Laboratory Work 2, Figure 7 and Figure 8). */
class OrderServiceTest {

    private final ShopFixture shop = new ShopFixture();
    private final Actor manager = Actor.staff(UUID.randomUUID());
    private UUID hoodie;

    @BeforeEach
    void setUp() {
        hoodie = shop.variant("HOOD-M", "1000", 5);
    }

    private CheckoutResult placeCardOrder() {
        return shop.checkout.placeOrder(shop.order(shop.cartWith(hoodie, 2), PaymentMethod.CARD_ONLINE),
                UUID.randomUUID().toString());
    }

    private void pay(CheckoutResult result, String eventId) {
        PaymentSession session = shop.payments.findSession(result.orderId()).orElseThrow();
        shop.payments.handleWebhook(new PaymentWebhookEvent(eventId, PaymentWebhookEvent.CHECKOUT_COMPLETED, session.sessionId()));
    }

    private int onHand() {
        return shop.inventory.availableQuantity(hoodie);
    }

    @Test
    void successfulPaymentMarksOrderPaidAndCommitsStock() {
        CheckoutResult result = placeCardOrder();

        pay(result, "evt_1");
        pay(result, "evt_1"); // duplicate delivery of the webhook

        Order order = shop.orderQueries.getByNumber(result.orderNumber());
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.history()).filteredOn(change -> change.toStatus() == OrderStatus.PAID).hasSize(1);
        assertThat(onHand()).isEqualTo(3);
    }

    @Test
    void unpaidOrderIsCancelledAfterThirtyMinutesAndStockIsReleased() {
        CheckoutResult result = placeCardOrder();
        assertThat(onHand()).isEqualTo(3);

        shop.clock.advance(Duration.ofMinutes(29));
        shop.orders.expireUnpaidOrders();
        assertThat(shop.orderQueries.getByNumber(result.orderNumber()).status()).isEqualTo(OrderStatus.NEW);

        shop.clock.advance(Duration.ofMinutes(1));
        shop.orders.expireUnpaidOrders();
        assertThat(shop.orderQueries.getByNumber(result.orderNumber()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(onHand()).isEqualTo(5);
        assertThat(shop.paymentProvider.expiredSessions()).hasSize(1);
    }

    @Test
    void paymentAfterCancellationIsRefunded() {
        CheckoutResult result = placeCardOrder();
        shop.clock.advance(Duration.ofMinutes(31));
        shop.orders.expireUnpaidOrders();

        pay(result, "evt_late");

        assertThat(shop.orderQueries.getByNumber(result.orderNumber()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(shop.paymentProvider.refundedSessions()).hasSize(1);
    }

    @Test
    void cancellingPaidOrderRestocksAndRefunds() {
        CheckoutResult result = placeCardOrder();
        pay(result, "evt_1");

        shop.orders.cancelOrder(result.orderId(), Actor.customer(shop.customerId), "changed my mind");

        assertThat(shop.orderQueries.getByNumber(result.orderNumber()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(onHand()).isEqualTo(5);
        assertThat(shop.paymentProvider.refundedSessions()).hasSize(1);
    }

    @Test
    void managerProcessesCashOrderUntilDelivery() {
        CheckoutResult result = shop.checkout.placeOrder(
                shop.order(shop.cartWith(hoodie, 1), PaymentMethod.CASH_ON_DELIVERY), "cash-1");

        shop.orders.changeStatus(result.orderId(), OrderStatus.PROCESSING, manager, null);
        shop.orders.changeStatus(result.orderId(), OrderStatus.SHIPPED, manager, "20450000000001");
        Order order = shop.orders.changeStatus(result.orderId(), OrderStatus.DELIVERED, manager, null);

        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(onHand()).isEqualTo(4);
        assertThat(shop.orderQueries.hasReceivedProduct(shop.customerId, shop.productOf(hoodie))).isTrue();
    }

    @Test
    void returnedOrderIsRestockedAndRefunded() {
        CheckoutResult result = placeCardOrder();
        pay(result, "evt_1");
        shop.orders.changeStatus(result.orderId(), OrderStatus.PROCESSING, manager, null);
        shop.orders.changeStatus(result.orderId(), OrderStatus.SHIPPED, manager, "123");
        shop.orders.changeStatus(result.orderId(), OrderStatus.DELIVERED, manager, null);

        shop.orders.changeStatus(result.orderId(), OrderStatus.RETURNED, manager, null);

        assertThat(onHand()).isEqualTo(5);
        assertThat(shop.paymentProvider.refundedSessions()).hasSize(1);
    }

    @Test
    void salesStatisticsIgnoreCancelledOrders() {
        CheckoutResult paid = placeCardOrder();
        pay(paid, "evt_1");
        CheckoutResult cancelled = placeCardOrder();
        shop.orders.cancelOrder(cancelled.orderId(), manager, "test");

        var summary = shop.orderQueries.salesSummary(shop.clock.instant().minusSeconds(60), shop.clock.instant().plusSeconds(60));

        assertThat(summary.ordersCount()).isEqualTo(1);
        assertThat(summary.revenue()).isEqualTo(paid.total());
    }
}
