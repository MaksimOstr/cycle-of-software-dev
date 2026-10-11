package com.cycleofsoftwaredev.inventory.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    StockItem save(StockItem item);

    Optional<StockItem> findByVariantId(UUID variantId);

    /**
     * Reads the stock of a variant and locks it until the end of the transaction, so that two orders
     * cannot take the same goods at the same time.
     */
    Optional<StockItem> lockByVariantId(UUID variantId);

    /** Stock of several variants in one query; only for reading, the rows are not locked. */
    List<StockItem> findByVariantIdIn(Collection<UUID> variantIds);

    /** Quantity that can still be ordered; empty if the variant has no stock record. */
    Optional<Integer> findAvailableQuantity(UUID variantId);
}
