package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderFilter;
import com.cycleofsoftwaredev.ordering.domain.OrderSearch;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Order list and sales statistics in PostgreSQL. The list is one query with WHERE, ORDER BY and LIMIT that
 * selects the ids of the page; the statistics are one aggregate query.
 */
@Repository
public class JdbcOrderSearch implements OrderSearch {

    private final JdbcClient jdbc;
    private final JpaOrderRepository orders;

    public JdbcOrderSearch(JdbcClient jdbc, JpaOrderRepository orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @Override
    public List<Order> find(OrderFilter filter, int offset, int limit) {
        // only the structure of the query depends on the filter; the values are always passed as parameters
        StringBuilder where = new StringBuilder("TRUE");
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (filter.status() != null) {
            where.append(" AND o.status = :status");
            parameters.put("status", filter.status().name());
        }
        if (filter.paymentMethod() != null) {
            where.append(" AND o.payment_method = :paymentMethod");
            parameters.put("paymentMethod", filter.paymentMethod().name());
        }
        if (filter.deliveryMethod() != null) {
            where.append(" AND o.delivery_method = :deliveryMethod");
            parameters.put("deliveryMethod", filter.deliveryMethod().name());
        }
        if (filter.from() != null) {
            where.append(" AND o.created_at >= :from");
            parameters.put("from", timestamp(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND o.created_at < :to");
            parameters.put("to", timestamp(filter.to()));
        }
        if (filter.text() != null) {
            where.append(" AND (strpos(lower(o.number), :text) > 0 OR strpos(lower(o.contact_email), :text) > 0");
            parameters.put("text", filter.text());
            if (filter.phoneDigits() != null) {
                where.append(" OR strpos(regexp_replace(o.contact_phone, '\\D', '', 'g'), :phoneDigits) > 0");
                parameters.put("phoneDigits", filter.phoneDigits());
            }
            where.append(")");
        }
        parameters.put("limit", limit);
        parameters.put("offset", offset);

        List<UUID> ids = jdbc.sql("SELECT o.id FROM ordering.orders o WHERE " + where
                        + " ORDER BY o.created_at DESC, o.number DESC LIMIT :limit OFFSET :offset")
                .params(parameters)
                .query(UUID.class)
                .list();
        Map<UUID, Order> loaded = orders.findAllById(ids).stream()
                .collect(Collectors.toMap(Order::id, Function.identity()));
        return ids.stream().map(loaded::get).toList();
    }

    @Override
    public SalesSummary salesSummary(Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT count(*) AS orders_count, coalesce(sum(o.total), 0) AS revenue
                        FROM ordering.orders o
                        WHERE o.created_at >= :from AND o.created_at < :to
                          AND o.status NOT IN ('CANCELLED', 'RETURNED')""")
                .param("from", timestamp(from))
                .param("to", timestamp(to))
                .query((row, index) -> new SalesSummary(row.getLong("orders_count"), Money.of(row.getBigDecimal("revenue"))))
                .single();
    }

    /** The PostgreSQL driver takes a "timestamp with time zone" parameter as OffsetDateTime. */
    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
