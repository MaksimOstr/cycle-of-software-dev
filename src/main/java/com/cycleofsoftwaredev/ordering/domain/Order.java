package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.ordering.api.OrderPlaced;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.OrderStatusChanged;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.cycleofsoftwaredev.ordering.api.OrderStatus.CANCELLED;
import static com.cycleofsoftwaredev.ordering.api.OrderStatus.DELIVERED;
import static com.cycleofsoftwaredev.ordering.api.OrderStatus.NEW;
import static com.cycleofsoftwaredev.ordering.api.OrderStatus.PAID;
import static com.cycleofsoftwaredev.ordering.api.OrderStatus.PROCESSING;
import static com.cycleofsoftwaredev.ordering.api.OrderStatus.RETURNED;
import static com.cycleofsoftwaredev.ordering.api.OrderStatus.SHIPPED;

/**
 * Order aggregate root. Enforces the order state machine (Laboratory Work 2, Figure 8) and records
 * the status history and the domain events for the other modules.
 */
public class Order {

    public static final Duration PAYMENT_TIME_LIMIT = Duration.ofMinutes(30);
    public static final Duration RETURN_PERIOD = Duration.ofDays(14);

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            NEW, EnumSet.of(PAID, PROCESSING, CANCELLED),
            PAID, EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.of(RETURNED),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            RETURNED, EnumSet.noneOf(OrderStatus.class));

    private final UUID id;
    private final String number;
    private final String idempotencyKey;
    private final UUID customerId;
    private final ContactInfo contact;
    private final PaymentMethod paymentMethod;
    private final List<OrderItem> items;
    private final String promoCode;
    private final Money subtotal;
    private final Money discount;
    private final Money deliveryCost;
    private final Money total;
    private final Instant createdAt;
    private final Instant paymentDeadline;
    private final List<OrderStatusChange> history = new ArrayList<>();
    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private DeliveryInfo delivery;
    private OrderStatus status;
    private Instant deliveredAt;

    private Order(UUID id, String number, String idempotencyKey, UUID customerId, ContactInfo contact,
                  DeliveryInfo delivery, PaymentMethod paymentMethod, List<OrderItem> items, String promoCode,
                  Money discount, Money deliveryCost, Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new BusinessRuleException("An order must contain at least one item");
        }
        this.id = Objects.requireNonNull(id);
        this.number = Objects.requireNonNull(number);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.customerId = customerId;
        this.contact = Objects.requireNonNull(contact);
        this.delivery = Objects.requireNonNull(delivery);
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.items = List.copyOf(items);
        this.promoCode = promoCode;
        this.subtotal = this.items.stream().map(OrderItem::lineTotal).reduce(Money.zero(), Money::add);
        this.discount = Objects.requireNonNull(discount);
        this.deliveryCost = Objects.requireNonNull(deliveryCost);
        if (discount.isNegative() || subtotal.compareTo(discount) < 0) {
            throw new BusinessRuleException("Discount must be between zero and the order subtotal");
        }
        this.total = subtotal.subtract(discount).add(deliveryCost);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.paymentDeadline = paymentMethod == PaymentMethod.CARD_ONLINE ? createdAt.plus(PAYMENT_TIME_LIMIT) : null;
    }

    /** Creates a new order in status NEW. */
    public static Order place(UUID id, String number, String idempotencyKey, UUID customerId, ContactInfo contact,
                              DeliveryInfo delivery, PaymentMethod paymentMethod, List<OrderItem> items,
                              String promoCode, Money discount, Money deliveryCost, Instant now) {
        Order order = new Order(id, number, idempotencyKey, customerId, contact, delivery, paymentMethod, items,
                promoCode, discount, deliveryCost, now);
        order.status = NEW;
        order.history.add(new OrderStatusChange(null, NEW, customerId, now, "Order placed"));
        order.pendingEvents.add(new OrderPlaced(id, number, contact.email(), contact.fullName(), order.total, now));
        return order;
    }

    /** Online payment succeeded. */
    public void markPaid(Instant now) {
        if (paymentMethod != PaymentMethod.CARD_ONLINE) {
            throw new BusinessRuleException("Order " + number + " is paid on delivery");
        }
        changeStatus(PAID, Actor.system(), "Payment received", now);
    }

    /** A manager starts processing: a paid order, or a new order with cash on delivery. */
    public void startProcessing(Actor actor, Instant now) {
        requireStaff(actor);
        if (status == NEW && paymentMethod == PaymentMethod.CARD_ONLINE) {
            throw new BusinessRuleException("Order " + number + " must be paid before processing");
        }
        changeStatus(PROCESSING, actor, "Processing started", now);
    }

    public void ship(String trackingNumber, Actor actor, Instant now) {
        requireStaff(actor);
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new BusinessRuleException("Tracking number is required to ship an order");
        }
        requireTransition(SHIPPED);
        delivery = delivery.withTrackingNumber(trackingNumber.trim());
        changeStatus(SHIPPED, actor, "Shipped", now);
    }

    public void markDelivered(Actor actor, Instant now) {
        if (!actor.isStaff() && actor.kind() != Actor.Kind.SYSTEM) {
            throw new BusinessRuleException("Only staff or the delivery service can confirm delivery");
        }
        changeStatus(DELIVERED, actor, "Delivered", now);
        deliveredAt = now;
    }

    public void cancel(Actor actor, String reason, Instant now) {
        if (!canBeCancelled()) {
            throw new BusinessRuleException("Order " + number + " cannot be cancelled in status " + status);
        }
        if (actor.kind() == Actor.Kind.CUSTOMER && !actor.userId().equals(customerId)) {
            throw new BusinessRuleException("Customers can cancel only their own orders");
        }
        changeStatus(CANCELLED, actor, reason, now);
    }

    public void markReturned(Actor actor, Instant now) {
        requireStaff(actor);
        if (!isReturnAllowed(now)) {
            throw new BusinessRuleException("Return is allowed only within " + RETURN_PERIOD.toDays() + " days after delivery");
        }
        changeStatus(RETURNED, actor, "Returned", now);
    }

    public boolean canBeCancelled() {
        return status == NEW || status == PAID || status == PROCESSING;
    }

    public boolean isReturnAllowed(Instant now) {
        return status == DELIVERED && now.isBefore(deliveredAt.plus(RETURN_PERIOD));
    }

    public boolean isPaymentOverdue(Instant now) {
        return status == NEW && paymentDeadline != null && !now.isBefore(paymentDeadline);
    }

    /** True if the customer has paid online, so cancellation or return requires a refund. */
    public boolean wasPaidOnline() {
        return history.stream().anyMatch(change -> change.toStatus() == PAID);
    }

    /** Returns the domain events recorded since the last call and forgets them. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    private void changeStatus(OrderStatus target, Actor actor, String comment, Instant now) {
        requireTransition(target);
        OrderStatus previous = status;
        status = target;
        history.add(new OrderStatusChange(previous, target, actor.userId(), now, comment));
        pendingEvents.add(new OrderStatusChanged(id, number, contact.email(), previous, target,
                delivery.trackingNumber(), now));
    }

    private void requireTransition(OrderStatus target) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(target)) {
            throw new BusinessRuleException("Order " + number + " cannot change status from " + status + " to " + target);
        }
    }

    private static void requireStaff(Actor actor) {
        if (!actor.isStaff()) {
            throw new BusinessRuleException("Only a manager or an administrator can do this");
        }
    }

    public UUID id() {
        return id;
    }

    public String number() {
        return number;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public UUID customerId() {
        return customerId;
    }

    public ContactInfo contact() {
        return contact;
    }

    public DeliveryInfo delivery() {
        return delivery;
    }

    public PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    public List<OrderItem> items() {
        return items;
    }

    public String promoCode() {
        return promoCode;
    }

    public Money subtotal() {
        return subtotal;
    }

    public Money discount() {
        return discount;
    }

    public Money deliveryCost() {
        return deliveryCost;
    }

    public Money total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant paymentDeadline() {
        return paymentDeadline;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public List<OrderStatusChange> history() {
        return Collections.unmodifiableList(history);
    }
}
