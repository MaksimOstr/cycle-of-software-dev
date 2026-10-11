package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderFilter;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.ordering.domain.OrderSearch;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** In-memory test double of the order queries: the same rules as the SQL queries, applied in Java. */
public class InMemoryOrderSearch implements OrderSearch {

    private static final Set<OrderStatus> NOT_SOLD = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURNED);

    private final OrderRepository orders;

    public InMemoryOrderSearch(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public List<Order> find(OrderFilter filter, int offset, int limit) {
        return orders.findAll().stream()
                .filter(filter::matches)
                .sorted(Comparator.comparing(Order::createdAt).reversed().thenComparing(Order::number, Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public SalesSummary salesSummary(Instant from, Instant to) {
        List<Order> sold = orders.findAll().stream()
                .filter(order -> !order.createdAt().isBefore(from) && order.createdAt().isBefore(to))
                .filter(order -> !NOT_SOLD.contains(order.status()))
                .toList();
        Money revenue = sold.stream().map(Order::total).reduce(Money.zero(), Money::add);
        return new SalesSummary(sold.size(), revenue);
    }
}
