package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.List;
import java.util.UUID;

/** Product as returned by the catalog and management endpoints, with available quantity of each variant. */
public record ProductResponse(UUID id, String name, String description, UUID categoryId, List<Variant> variants) {

    public record Variant(UUID id, String sku, String name, Money price, int availableQuantity) {
    }
}
