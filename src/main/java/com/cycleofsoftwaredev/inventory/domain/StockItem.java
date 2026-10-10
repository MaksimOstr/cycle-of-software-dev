package com.cycleofsoftwaredev.inventory.domain;

import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import java.util.Objects;
import java.util.UUID;

/** Stock of one product variant: goods on hand and the part of them reserved for unpaid orders. */
public class StockItem {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final UUID variantId;
    private int onHand;
    private int reserved;
    private int lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD;

    public StockItem(UUID variantId, int onHand) {
        this.variantId = Objects.requireNonNull(variantId);
        setOnHand(onHand);
    }

    public int available() {
        return onHand - reserved;
    }

    public boolean isLowStock() {
        return available() <= lowStockThreshold;
    }

    public void setOnHand(int newOnHand) {
        if (newOnHand < reserved) {
            throw new IllegalArgumentException("On-hand quantity cannot be less than reserved quantity " + reserved);
        }
        this.onHand = newOnHand;
    }

    public void reserve(int quantity) {
        if (available() < quantity) {
            throw new OutOfStockException(variantId, available());
        }
        reserved += quantity;
    }

    public void commitReserved(int quantity) {
        reserved -= quantity;
        onHand -= quantity;
    }

    public void releaseReserved(int quantity) {
        reserved -= quantity;
    }

    public void putBack(int quantity) {
        onHand += quantity;
    }

    public UUID variantId() {
        return variantId;
    }

    public int onHand() {
        return onHand;
    }

    public int reserved() {
        return reserved;
    }

    public int lowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold must not be negative");
        }
        this.lowStockThreshold = threshold;
    }
}
