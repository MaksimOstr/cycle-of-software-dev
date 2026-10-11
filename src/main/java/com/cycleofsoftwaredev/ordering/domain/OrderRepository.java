package com.cycleofsoftwaredev.ordering.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage abstraction for orders (Laboratory Work 2, class diagram of the ordering services). */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    /**
     * Reads the order and locks it until the end of the transaction, so that a payment notification,
     * a manager and the expiry job cannot change the status of one order at the same time.
     */
    Optional<Order> lockById(UUID id);

    Optional<Order> findByNumber(String number);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /** Ids of unpaid card orders whose payment deadline has passed. */
    List<UUID> findOverdueIds(Instant now);

    List<Order> findByCustomerId(UUID customerId);

    List<Order> findAll();
}
