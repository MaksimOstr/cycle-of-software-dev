package com.cycleofsoftwaredev.inventory.application;

import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.inventory.api.StockLine;
import com.cycleofsoftwaredev.inventory.domain.ReservationRepository;
import com.cycleofsoftwaredev.inventory.domain.ReservationStatus;
import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockRepository;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases "Stock management" and "Stock reservation". The methods are synchronized so that two customers
 * cannot reserve the last item at the same time; with PostgreSQL this becomes a conditional UPDATE.
 */
@Service
public class InventoryService implements InventoryApi {

    private final StockRepository stock;
    private final ReservationRepository reservations;

    public InventoryService(StockRepository stock, ReservationRepository reservations) {
        this.stock = stock;
        this.reservations = reservations;
    }

    /** Manager sets the quantity of goods on hand for a variant. */
    public synchronized StockItem setStock(UUID variantId, int onHand) {
        if (onHand < 0) {
            throw new IllegalArgumentException("Quantity must not be negative");
        }
        StockItem item = stock.findByVariantId(variantId).orElseGet(() -> new StockItem(variantId, 0));
        item.setOnHand(onHand);
        return stock.save(item);
    }

    @Override
    public synchronized int availableQuantity(UUID variantId) {
        return stock.findByVariantId(variantId).map(StockItem::available).orElse(0);
    }

    @Override
    public synchronized void reserve(UUID orderId, List<StockLine> lines, Instant expiresAt) {
        // check every line first, so that either all lines are reserved or none of them
        for (StockLine line : lines) {
            int available = availableQuantity(line.variantId());
            if (available < line.quantity()) {
                throw new OutOfStockException(line.variantId(), available);
            }
        }
        for (StockLine line : lines) {
            StockItem item = stock.findByVariantId(line.variantId()).orElseThrow();
            item.reserve(line.quantity());
            stock.save(item);
            reservations.save(new StockReservation(UUID.randomUUID(), orderId, line.variantId(), line.quantity(), expiresAt));
        }
    }

    @Override
    public synchronized void commit(UUID orderId) {
        for (StockReservation reservation : reservationsOf(orderId, ReservationStatus.ACTIVE)) {
            StockItem item = stock.findByVariantId(reservation.variantId()).orElseThrow();
            item.commitReserved(reservation.quantity());
            reservation.markCommitted();
            stock.save(item);
            reservations.save(reservation);
        }
    }

    @Override
    public synchronized void release(UUID orderId) {
        for (StockReservation reservation : reservationsOf(orderId, ReservationStatus.ACTIVE)) {
            StockItem item = stock.findByVariantId(reservation.variantId()).orElseThrow();
            item.releaseReserved(reservation.quantity());
            reservation.markReleased();
            stock.save(item);
            reservations.save(reservation);
        }
    }

    @Override
    public synchronized void restock(UUID orderId) {
        for (StockReservation reservation : reservationsOf(orderId, ReservationStatus.COMMITTED)) {
            StockItem item = stock.findByVariantId(reservation.variantId()).orElseThrow();
            item.putBack(reservation.quantity());
            reservation.markRestocked();
            stock.save(item);
            reservations.save(reservation);
        }
    }

    private List<StockReservation> reservationsOf(UUID orderId, ReservationStatus status) {
        return reservations.findByOrderId(orderId).stream()
                .filter(reservation -> reservation.status() == status)
                .toList();
    }
}
