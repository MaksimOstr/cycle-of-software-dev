package com.cycleofsoftwaredev.inventory.infrastructure;

import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
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
}
