package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.cart.api.CartApi;
import com.cycleofsoftwaredev.cart.api.CartSnapshot;
import com.cycleofsoftwaredev.delivery.api.DeliveryApi;
import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import com.cycleofsoftwaredev.inventory.api.StockLine;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.domain.DeliveryInfo;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderItem;
import com.cycleofsoftwaredev.ordering.domain.OrderNumberGenerator;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.payment.api.PaymentApi;
import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.api.PaymentSession;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use case "Checkout" (Laboratory Work 2, sequence diagram "placing an order").
 * Depends only on abstractions: repository and number generator of its own module and the public APIs
 * of Cart, Delivery, Inventory and Payment (Dependency Inversion principle).
 */
@Service
public class CheckoutService {

    private final OrderRepository orders;
    private final OrderNumberGenerator numberGenerator;
    private final CartApi cartApi;
    private final DeliveryApi deliveryApi;
    private final InventoryApi inventoryApi;
    private final PaymentApi paymentApi;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CheckoutService(OrderRepository orders, OrderNumberGenerator numberGenerator, CartApi cartApi,
                           DeliveryApi deliveryApi, InventoryApi inventoryApi, PaymentApi paymentApi,
                           DomainEventPublisher events, Clock clock) {
        this.orders = orders;
        this.numberGenerator = numberGenerator;
        this.cartApi = cartApi;
        this.deliveryApi = deliveryApi;
        this.inventoryApi = inventoryApi;
        this.paymentApi = paymentApi;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Places an order from the cart. A repeated request with the same idempotency key returns the order
     * created by the first request instead of creating a second one.
     */
    public synchronized CheckoutResult placeOrder(PlaceOrderCommand command, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResult(existing.get());
        }

        CartSnapshot cart = cartApi.getCheckoutCart(command.cartId());
        if (cart.isEmpty()) {
            throw new BusinessRuleException("The cart is empty");
        }
        List<OrderItem> items = cart.lines().stream()
                .map(line -> new OrderItem(line.variantId(), line.productId(), line.sku(), line.productName(),
                        line.unitPrice(), line.quantity()))
                .toList();
        Money deliveryCost = deliveryApi.calculateCost(command.deliveryMethod(), cart.totalAfterDiscount());

        Instant now = clock.instant();
        Order order = Order.place(UUID.randomUUID(), numberGenerator.nextNumber(), idempotencyKey,
                command.customerId(), command.contact(),
                DeliveryInfo.of(command.deliveryMethod(), command.deliveryAddress()),
                command.paymentMethod(), items, cart.promoCode(), cart.discount(), deliveryCost, now);

        List<StockLine> stockLines = items.stream().map(item -> new StockLine(item.variantId(), item.quantity())).toList();
        inventoryApi.reserve(order.id(), stockLines, order.paymentDeadline());
        orders.save(order);
        order.pullEvents().forEach(events::publish);
        cartApi.completeCheckout(command.cartId(), order.id());

        if (order.paymentMethod() == PaymentMethod.CARD_ONLINE) {
            PaymentSession session = paymentApi.startPayment(new PaymentRequest(order.id(), order.number(), order.total()));
            return new CheckoutResult(order.id(), order.number(), order.status(), order.total(), session.paymentUrl());
        }
        return toResult(order);
    }

    private CheckoutResult toResult(Order order) {
        String paymentUrl = null;
        if (order.paymentMethod() == PaymentMethod.CARD_ONLINE && order.status() == OrderStatus.NEW) {
            paymentUrl = paymentApi.findSession(order.id()).map(PaymentSession::paymentUrl).orElse(null);
        }
        return new CheckoutResult(order.id(), order.number(), order.status(), order.total(), paymentUrl);
    }
}
