package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** There is nothing to lock in a single-threaded unit test. */
    @Override
    public Optional<Order> lockById(UUID id) {
        return findById(id);
    }

    @Override
    public Optional<Order> findByNumber(String number) {
        return orders.values().stream().filter(order -> order.number().equals(number)).findFirst();
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return orders.values().stream().filter(order -> order.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override
    public List<UUID> findOverdueIds(Instant now) {
        return orders.values().stream()
                .filter(order -> order.isPaymentOverdue(now))
                .sorted(Comparator.comparing(Order::createdAt))
                .map(Order::id)
                .toList();
    }

    @Override
    public List<Order> findByCustomerId(UUID customerId) {
        return orders.values().stream().filter(order -> Objects.equals(order.customerId(), customerId)).toList();
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }
}
