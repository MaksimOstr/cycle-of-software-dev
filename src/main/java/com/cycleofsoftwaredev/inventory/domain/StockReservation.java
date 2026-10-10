package com.cycleofsoftwaredev.inventory.domain;

import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Goods of one variant held for one order. */
public class StockReservation {

    private final UUID id;
    private final UUID orderId;
    private final UUID variantId;
    private final int quantity;
    private final Instant expiresAt;
    private ReservationStatus status = ReservationStatus.ACTIVE;

    public StockReservation(UUID id, UUID orderId, UUID variantId, int quantity, Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.variantId = Objects.requireNonNull(variantId);
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    public void markCommitted() {
        requireStatus(ReservationStatus.ACTIVE);
        status = ReservationStatus.COMMITTED;
    }

    public void markReleased() {
        requireStatus(ReservationStatus.ACTIVE);
        status = ReservationStatus.RELEASED;
    }

    public void markRestocked() {
        requireStatus(ReservationStatus.COMMITTED);
        status = ReservationStatus.RESTOCKED;
    }

    private void requireStatus(ReservationStatus expected) {
        if (status != expected) {
            throw new BusinessRuleException("Reservation " + id + " is " + status + ", expected " + expected);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID variantId() {
        return variantId;
    }

    public int quantity() {
        return quantity;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public ReservationStatus status() {
        return status;
    }
}
