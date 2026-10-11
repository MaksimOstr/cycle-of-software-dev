package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.PurchaseVerificationApi;
import com.cycleofsoftwaredev.ordering.api.SalesStatisticsApi;
import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderFilter;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.ordering.domain.OrderSearch;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the Ordering module: order history, order list, purchase verification and sales statistics. */
@Service
@Transactional(readOnly = true)
public class OrderQueryService implements PurchaseVerificationApi, SalesStatisticsApi {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final OrderRepository orders;
    private final OrderSearch orderSearch;

    public OrderQueryService(OrderRepository orders, OrderSearch orderSearch) {
        this.orders = orders;
        this.orderSearch = orderSearch;
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

    /**
     * Use case "Order list" of the Management Panel: one page of the orders that match the filter, newest
     * first. Pages are numbered from 1; the storage filters, sorts and cuts the page.
     */
    public List<Order> findOrders(OrderFilter filter, int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("Page number starts from 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        int offset = Math.toIntExact(Math.min((long) (page - 1) * size, Integer.MAX_VALUE));
        return orderSearch.find(filter, offset, size);
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
        return orderSearch.salesSummary(from, to);
    }
}
