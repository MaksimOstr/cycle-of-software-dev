package com.cycleofsoftwaredev.inventory.api;

import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import java.util.UUID;

/** Not enough goods to reserve; the customer sees "Only N left". */
public class OutOfStockException extends BusinessRuleException {

    private final UUID variantId;
    private final int available;

    public OutOfStockException(UUID variantId, int available) {
        super("Only " + available + " item(s) left for variant " + variantId);
        this.variantId = variantId;
        this.available = available;
    }

    public UUID variantId() {
        return variantId;
    }

    public int available() {
        return available;
    }
}
