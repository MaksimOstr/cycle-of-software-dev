package com.cycleofsoftwaredev.inventory.api;

import java.util.Objects;
import java.util.UUID;

public record StockLine(UUID variantId, int quantity) {

    public StockLine {
        Objects.requireNonNull(variantId);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
