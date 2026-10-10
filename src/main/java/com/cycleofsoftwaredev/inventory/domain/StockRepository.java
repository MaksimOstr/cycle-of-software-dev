package com.cycleofsoftwaredev.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    StockItem save(StockItem item);

    Optional<StockItem> findByVariantId(UUID variantId);
}
