package com.cycleofsoftwaredev.cart.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.UUID;

public record CartLine(UUID variantId, UUID productId, String sku, String productName, Money unitPrice, int quantity) {

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
