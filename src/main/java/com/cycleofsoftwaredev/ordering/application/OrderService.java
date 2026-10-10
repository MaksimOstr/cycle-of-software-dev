package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.payment.api.PaymentApi;
import com.cycleofsoftwaredev.payment.api.PaymentSucceeded;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Use cases after checkout: payment result, status update by managers, cancellation, return and
 * automatic cancellation of unpaid orders (Laboratory Work 2, sequence diagram "payment result").
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final InventoryApi inventoryApi;
    private final PaymentApi paymentApi;
    private final DomainEventPublisher events;
    private final Clock clock;

    public OrderService(OrderRepository orders, InventoryApi inventoryApi, PaymentApi paymentApi,
                        DomainEventPublisher events, Clock clock) {
        this.orders = orders;
        this.inventoryApi = inventoryApi;
        this.paymentApi = paymentApi;
        this.events = events;
        this.clock = clock;
    }

    @EventListener
    public synchronized void onPaymentSucceeded(PaymentSucceeded event) {
        Order order = getOrder(event.orderId());
        if (order.status() == OrderStatus.NEW) {
            order.markPaid(clock.instant());
            inventoryApi.commit(order.id());
            save(order);
        } else if (order.status() == OrderStatus.CANCELLED) {
            // the order was cancelled for non-payment, but the money still arrived: return it
            paymentApi.refund(order.id());
        }
    }

    /** Status update by a manager (Processing, Shipped, Delivered, Cancelled or Returned). */
    public synchronized Order changeStatus(UUID orderId, OrderStatus target, Actor actor, String trackingNumber) {
        Order order = getOrder(orderId);
        Instant now = clock.instant();
        switch (target) {
            case PROCESSING -> {
                boolean confirmsCashOrder = order.status() == OrderStatus.NEW;
                order.startProcessing(actor, now);
                if (confirmsCashOrder) {
                    inventoryApi.commit(order.id());
                }
            }
            case SHIPPED -> order.ship(trackingNumber, actor, now);
            case DELIVERED -> order.markDelivered(actor, now);
            case CANCELLED -> {
                return cancelOrder(orderId, actor, "Cancelled by manager");
            }
            case RETURNED -> {
                order.markReturned(actor, now);
                inventoryApi.restock(order.id());
                if (order.wasPaidOnline()) {
                    paymentApi.refund(order.id());
                }
            }
            default -> throw new BusinessRuleException("Status " + target + " cannot be set manually");
        }
        return save(order);
    }

    /** Use case "Order cancellation" by the customer or a manager. */
    public synchronized Order cancelOrder(UUID orderId, Actor actor, String reason) {
        Order order = getOrder(orderId);
        boolean stockCommitted = order.status() != OrderStatus.NEW;
        order.cancel(actor, reason, clock.instant());
        if (stockCommitted) {
            inventoryApi.restock(order.id());
        } else {
            inventoryApi.release(order.id());
            paymentApi.cancelPayment(order.id());
        }
        if (order.wasPaidOnline()) {
            paymentApi.refund(order.id());
        }
        return save(order);
    }

    /** Cancels card orders that were not paid within 30 minutes and releases their stock. */
    @Scheduled(fixedDelayString = "${shopsphere.ordering.unpaid-check-interval:PT1M}")
    public synchronized void expireUnpaidOrders() {
        Instant now = clock.instant();
        for (Order order : orders.findByStatus(OrderStatus.NEW)) {
            if (order.isPaymentOverdue(now)) {
                cancelOrder(order.id(), Actor.system(), "Not paid within " + Order.PAYMENT_TIME_LIMIT.toMinutes() + " minutes");
            }
        }
    }

    private Order save(Order order) {
        orders.save(order);
        order.pullEvents().forEach(events::publish);
        return order;
    }

    private Order getOrder(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}
