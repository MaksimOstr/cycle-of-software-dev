package com.cycleofsoftwaredev.cart.domain;

import java.util.Objects;
import java.util.UUID;

/** A variant in the cart. The price is not stored: it is always taken from the catalog. */
public record CartItem(UUID variantId, int quantity) {

    public static final int MAX_QUANTITY = 99;

    public CartItem {
        Objects.requireNonNull(variantId);
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QUANTITY);
        }
    }
}
