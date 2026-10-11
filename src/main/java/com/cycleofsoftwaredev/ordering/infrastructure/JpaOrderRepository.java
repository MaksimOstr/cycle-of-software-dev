package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PostgreSQL storage of orders with their items and status history. */
public interface JpaOrderRepository extends JpaRepository<Order, UUID>, OrderRepository {

    /** SELECT ... FOR UPDATE: other transactions wait until this one is finished. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") UUID id);

    @Override
    @Query("""
            select o.id from Order o
            where o.status = com.cycleofsoftwaredev.ordering.api.OrderStatus.NEW and o.paymentDeadline <= :now
            order by o.createdAt""")
    List<UUID> findOverdueIds(@Param("now") Instant now);
}
