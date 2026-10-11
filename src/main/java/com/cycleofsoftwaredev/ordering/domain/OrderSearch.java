package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import java.time.Instant;
import java.util.List;

/**
 * Queries over all orders for the Management Panel and the sales dashboard. They are executed by the
 * storage: the application never loads the whole table of orders.
 */
public interface OrderSearch {

    /** Orders that match the filter, newest first: {@code limit} of them starting from {@code offset}. */
    List<Order> find(OrderFilter filter, int offset, int limit);

    /** Number and revenue of the orders created in the period, without cancelled and returned ones. */
    SalesSummary salesSummary(Instant from, Instant to);
}
