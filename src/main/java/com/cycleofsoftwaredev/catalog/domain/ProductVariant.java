package com.cycleofsoftwaredev.catalog.domain;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/** A concrete purchasable version of a product (for example, size M, blue colour) with its own SKU and price. */
public record ProductVariant(UUID id, String sku, String name, Money price) {

    public ProductVariant {
        Objects.requireNonNull(id);
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU must not be blank");
        }
        Objects.requireNonNull(price);
        if (price.isNegative() || price.isZero()) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }
}
