package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage abstraction for orders (Laboratory Work 2, class diagram of the ordering services). */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByNumber(String number);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerId(UUID customerId);

    List<Order> findAll();
}
