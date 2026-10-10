package com.cycleofsoftwaredev.cart.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.List;
import java.util.UUID;

public record CartSnapshot(UUID cartId, List<CartLine> lines, Money subtotal, String promoCode, Money discount) {

    public CartSnapshot {
        lines = List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public Money totalAfterDiscount() {
        return subtotal.subtract(discount);
    }
}
