package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.PurchaseVerificationApi;
import com.cycleofsoftwaredev.ordering.api.SalesStatisticsApi;
import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read side of the Ordering module: order history, purchase verification and sales statistics. */
@Service
public class OrderQueryService implements PurchaseVerificationApi, SalesStatisticsApi {

    private static final Set<OrderStatus> NOT_SOLD = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURNED);

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    public Order getByNumber(String number) {
        return orders.findByNumber(number).orElseThrow(() -> new NotFoundException("Order", number));
    }

    /** Use case "Order history and tracking": orders of a customer, newest first. */
    public List<Order> customerOrders(UUID customerId) {
        return orders.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
    }

    @Override
    public boolean hasReceivedProduct(UUID customerId, UUID productId) {
        return orders.findByCustomerId(customerId).stream()
                .filter(order -> order.status() == OrderStatus.DELIVERED)
                .flatMap(order -> order.items().stream())
                .anyMatch(item -> item.productId().equals(productId));
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
