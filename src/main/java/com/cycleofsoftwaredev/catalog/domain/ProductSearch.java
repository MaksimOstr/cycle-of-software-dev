package com.cycleofsoftwaredev.catalog.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Search of the storefront catalog. Filtering, sorting and paging are done by the storage, so a page of
 * 24 products never requires loading the whole catalog.
 */
public interface ProductSearch {

    /**
     * Visible products in the given order, {@code limit} of them starting from {@code offset}.
     *
     * @param text        lower-case text that the name or the description must contain, or null for any product
     * @param categoryIds categories the product may belong to, or null for any category
     */
    List<Product> find(String text, Collection<UUID> categoryIds, ProductSort sort, int offset, int limit);

    /** Number of visible products that match the same conditions. */
    int count(String text, Collection<UUID> categoryIds);
}
