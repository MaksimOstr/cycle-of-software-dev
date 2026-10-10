package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
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

    @Override
    public Optional<Order> findByNumber(String number) {
        return orders.values().stream().filter(order -> order.number().equals(number)).findFirst();
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return orders.values().stream().filter(order -> order.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return orders.values().stream().filter(order -> order.status() == status).toList();
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
