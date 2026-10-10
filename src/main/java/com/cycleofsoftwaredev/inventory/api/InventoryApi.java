package com.cycleofsoftwaredev.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public interface of the Inventory module (Laboratory Work 2, class diagram of the ordering services).
 * Used by Cart (available quantity) and by Ordering (reservation lifecycle of an order).
 */
public interface InventoryApi {

    int availableQuantity(UUID variantId);

    /** Reserves all lines of an order or none of them; throws {@link OutOfStockException}. */
    void reserve(UUID orderId, List<StockLine> lines, Instant expiresAt);

    /** The order is paid or confirmed: reserved goods leave the stock. */
    void commit(UUID orderId);

    /** The order was cancelled before commit: reserved goods become available again. */
    void release(UUID orderId);

    /** The order was cancelled or returned after commit: goods are put back on the shelf. */
    void restock(UUID orderId);
}
