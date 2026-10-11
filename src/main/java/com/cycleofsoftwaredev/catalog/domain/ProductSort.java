package com.cycleofsoftwaredev.catalog.domain;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.Comparator;

/** Sorting of product lists. Products without variants have no price and go to the end of a price sorting. */
public enum ProductSort {

    NAME(Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER)),
    PRICE_ASC(Comparator.comparing(ProductSort::priceOrNull, Comparator.nullsLast(Comparator.naturalOrder()))),
    PRICE_DESC(Comparator.comparing(ProductSort::priceOrNull, Comparator.nullsLast(Comparator.reverseOrder())));

    private final Comparator<Product> comparator;

    ProductSort(Comparator<Product> comparator) {
        this.comparator = comparator;
    }

    /** The same order in Java; the storefront gets it from the database (see ProductSearch). */
    public Comparator<Product> comparator() {
        // products with equal keys keep a stable order between pages
        return comparator.thenComparing(Product::name).thenComparing(Product::id);
    }

    private static Money priceOrNull(Product product) {
        return product.lowestPrice().orElse(null);
    }
}
