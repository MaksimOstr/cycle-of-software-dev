package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductSearch;
import com.cycleofsoftwaredev.catalog.domain.ProductSort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Catalog search in PostgreSQL. One query with WHERE, ORDER BY and LIMIT selects the ids of the products of
 * the requested page, then only these products are loaded with their variants.
 */
@Repository
public class JdbcProductSearch implements ProductSearch {

    /** The cheapest variant is the price of a product in the list. */
    private static final String PRICE = "(SELECT min(v.price) FROM catalog.product_variants v WHERE v.product_id = p.id)";
    /** "C" collation compares characters by their codes, exactly like String.compareTo in Java. */
    private static final String BY_NAME = "p.name COLLATE \"C\", p.id";

    private final JdbcClient jdbc;
    private final JpaProductRepository products;

    public JdbcProductSearch(JdbcClient jdbc, JpaProductRepository products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    @Override
    public List<Product> find(String text, Collection<UUID> categoryIds, ProductSort sort, int offset, int limit) {
        if (categoryIds != null && categoryIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT p.id FROM catalog.products p WHERE " + conditions(text, categoryIds)
                + " ORDER BY " + orderBy(sort) + " LIMIT :limit OFFSET :offset";
        List<UUID> ids = parameters(jdbc.sql(sql), text, categoryIds)
                .param("limit", limit)
                .param("offset", offset)
                .query(UUID.class)
                .list();
        Map<UUID, Product> loaded = products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        return ids.stream().map(loaded::get).toList();
    }

    @Override
    public int count(String text, Collection<UUID> categoryIds) {
        if (categoryIds != null && categoryIds.isEmpty()) {
            return 0;
        }
        String sql = "SELECT count(*) FROM catalog.products p WHERE " + conditions(text, categoryIds);
        return parameters(jdbc.sql(sql), text, categoryIds).query(Integer.class).single();
    }

    /** Only the structure of the query depends on the filters; the values are always passed as parameters. */
    private static String conditions(String text, Collection<UUID> categoryIds) {
        StringBuilder where = new StringBuilder("p.active");
        if (text != null) {
            // strpos looks for the text as it is, so characters like % and _ have no special meaning
            where.append(" AND strpos(lower(p.name || ' ' || p.description), :text) > 0");
        }
        if (categoryIds != null) {
            where.append(" AND p.category_id IN (:categoryIds)");
        }
        return where.toString();
    }

    private static JdbcClient.StatementSpec parameters(JdbcClient.StatementSpec statement, String text,
                                                       Collection<UUID> categoryIds) {
        if (text != null) {
            statement = statement.param("text", text);
        }
        if (categoryIds != null) {
            statement = statement.param("categoryIds", categoryIds);
        }
        return statement;
    }

    private static String orderBy(ProductSort sort) {
        return switch (sort) {
            case NAME -> "lower(p.name) COLLATE \"C\", " + BY_NAME;
            case PRICE_ASC -> PRICE + " ASC NULLS LAST, " + BY_NAME;
            case PRICE_DESC -> PRICE + " DESC NULLS LAST, " + BY_NAME;
        };
    }
}
