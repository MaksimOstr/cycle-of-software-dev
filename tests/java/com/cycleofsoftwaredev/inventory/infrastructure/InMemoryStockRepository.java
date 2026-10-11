package com.cycleofsoftwaredev.inventory.infrastructure;

import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryStockRepository implements StockRepository {

    private final Map<UUID, StockItem> items = new ConcurrentHashMap<>();

    @Override
    public StockItem save(StockItem item) {
        items.put(item.variantId(), item);
        return item;
    }

    @Override
    public Optional<StockItem> findByVariantId(UUID variantId) {
        return Optional.ofNullable(items.get(variantId));
    }

    /** There is nothing to lock in a single-threaded unit test. */
    @Override
    public Optional<StockItem> lockByVariantId(UUID variantId) {
        return findByVariantId(variantId);
    }

    @Override
    public List<StockItem> findByVariantIdIn(Collection<UUID> variantIds) {
        return variantIds.stream().map(items::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<Integer> findAvailableQuantity(UUID variantId) {
        return findByVariantId(variantId).map(StockItem::available);
    }
}
