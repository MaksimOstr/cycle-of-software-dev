package com.cycleofsoftwaredev.inventory.domain;

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

    /** Quantity that can still be ordered; empty if the variant has no stock record. */
    Optional<Integer> findAvailableQuantity(UUID variantId);
}
