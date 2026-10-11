package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import com.cycleofsoftwaredev.catalog.domain.ProductSearch;
import com.cycleofsoftwaredev.catalog.domain.ProductSort;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** In-memory test double of the catalog search: the same rules as the SQL query, applied in Java. */
public class InMemoryProductSearch implements ProductSearch {

    private final ProductRepository products;

    public InMemoryProductSearch(ProductRepository products) {
        this.products = products;
    }

    @Override
    public List<Product> find(String text, Collection<UUID> categoryIds, ProductSort sort, int offset, int limit) {
        return matching(text, categoryIds).sorted(sort.comparator()).skip(offset).limit(limit).toList();
    }

    @Override
    public int count(String text, Collection<UUID> categoryIds) {
        return (int) matching(text, categoryIds).count();
    }

    private Stream<Product> matching(String text, Collection<UUID> categoryIds) {
        return products.findAll().stream()
                .filter(Product::isActive)
                .filter(product -> categoryIds == null || categoryIds.contains(product.categoryId()))
                .filter(product -> product.matches(text));
    }
}
