package com.cycleofsoftwaredev.catalog.application;

import com.cycleofsoftwaredev.catalog.domain.Product;
import java.util.List;

/** One page of a product list. Pages are numbered from 1. */
public record ProductPage(List<Product> items, int page, int size, int totalItems) {

    /** Requirement "Product listing": 24 products per page. */
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 100;

    public static ProductPage of(List<Product> sorted, int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("Page number starts from 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE);
        }
        long from = (long) (page - 1) * size;
        List<Product> items = sorted.stream().skip(from).limit(size).toList();
        return new ProductPage(items, page, size, sorted.size());
    }

    public int totalPages() {
        return (totalItems + size - 1) / size;
    }
}
