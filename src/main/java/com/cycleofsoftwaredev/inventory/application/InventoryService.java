package com.cycleofsoftwaredev.inventory.application;

import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.inventory.api.StockLine;
import com.cycleofsoftwaredev.inventory.domain.ReservationRepository;
import com.cycleofsoftwaredev.inventory.domain.ReservationStatus;
import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockRepository;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases "Stock management" and "Stock reservation". Every change runs in a transaction and locks the
 * stock rows it changes (SELECT ... FOR UPDATE), so two customers cannot reserve the last item at the same
 * time. Rows are always locked in the order of variant ids, which prevents deadlocks between orders.
 */
@Service
@Transactional
public class InventoryService implements InventoryApi {

    private final StockRepository stock;
    private final ReservationRepository reservations;

    public InventoryService(StockRepository stock, ReservationRepository reservations) {
        this.stock = stock;
        this.reservations = reservations;
    }

    /** Manager sets the quantity of goods on hand for a variant. */
    public StockItem setStock(UUID variantId, int onHand) {
        if (onHand < 0) {
            throw new IllegalArgumentException("Quantity must not be negative");
        }
        StockItem item = stock.lockByVariantId(variantId).orElseGet(() -> new StockItem(variantId, 0));
        item.setOnHand(onHand);
        return stock.save(item);
    }

    @Override
    @Transactional(readOnly = true)
    public int availableQuantity(UUID variantId) {
        return stock.findAvailableQuantity(variantId).orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> availableQuantities(Collection<UUID> variantIds) {
        Map<UUID, Integer> available = new HashMap<>();
        variantIds.forEach(variantId -> available.put(variantId, 0));
        if (!variantIds.isEmpty()) {
            stock.findByVariantIdIn(variantIds).forEach(item -> available.put(item.variantId(), item.available()));
        }
        return available;
    }

    @Override
    public void reserve(UUID orderId, List<StockLine> lines, Instant expiresAt) {
        if (!reservations.findByOrderId(orderId).isEmpty()) {
            throw new BusinessRuleException("Stock for order " + orderId + " is already reserved");
        }
        // several lines of one variant are reserved as one quantity
        Map<UUID, Integer> quantities = new TreeMap<>();
        lines.forEach(line -> quantities.merge(line.variantId(), line.quantity(), Integer::sum));

        // lock and check every variant first, so that either all lines are reserved or none of them
        Map<UUID, StockItem> items = new LinkedHashMap<>();
        quantities.forEach((variantId, quantity) -> {
            StockItem item = stock.lockByVariantId(variantId).orElseThrow(() -> new OutOfStockException(variantId, 0));
            if (item.available() < quantity) {
                throw new OutOfStockException(variantId, item.available());
            }
            items.put(variantId, item);
        });

        quantities.forEach((variantId, quantity) -> {
            StockItem item = items.get(variantId);
            item.reserve(quantity);
            stock.save(item);
            reservations.save(new StockReservation(UUID.randomUUID(), orderId, variantId, quantity, expiresAt));
        });
    }

    @Override
    public void commit(UUID orderId) {
        for (StockReservation reservation : reservationsOf(orderId, ReservationStatus.ACTIVE)) {
            StockItem item = lockStock(reservation);
            item.commitReserved(reservation.quantity());
            reservation.markCommitted();
            stock.save(item);
            reservations.save(reservation);
        }
    }

    @Override
    public void release(UUID orderId) {
        for (StockReservation reservation : reservationsOf(orderId, ReservationStatus.ACTIVE)) {
            StockItem item = lockStock(reservation);
            item.releaseReserved(reservation.quantity());
            reservation.markReleased();
            stock.save(item);
            reservations.save(reservation);
        }
    }

    @Override
    public void restock(UUID orderId) {
        for (StockReservation reservation : reservationsOf(orderId, ReservationStatus.COMMITTED)) {
            StockItem item = lockStock(reservation);
            item.putBack(reservation.quantity());
            reservation.markRestocked();
            stock.save(item);
            reservations.save(reservation);
        }
    }

    private StockItem lockStock(StockReservation reservation) {
        return stock.lockByVariantId(reservation.variantId()).orElseThrow();
    }

    private List<StockReservation> reservationsOf(UUID orderId, ReservationStatus status) {
        return reservations.findByOrderId(orderId).stream()
                .filter(reservation -> reservation.status() == status)
                .sorted(Comparator.comparing(StockReservation::variantId))
                .toList();
    }
}
