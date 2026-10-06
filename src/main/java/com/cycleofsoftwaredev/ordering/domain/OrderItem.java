package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/** Ordered variant. Name and price are copied, so later catalog changes do not change the order. */
public record OrderItem(UUID variantId, UUID productId, String sku, String productName, Money unitPrice, int quantity) {

    public OrderItem {
        Objects.requireNonNull(variantId);
        Objects.requireNonNull(productId);
        Objects.requireNonNull(unitPrice);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
