package com.cycleofsoftwaredev.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.inventory.api.StockLine;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.inventory.domain.ReservationRepository;
import com.cycleofsoftwaredev.inventory.domain.ReservationStatus;
import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockRepository;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inventory module with the real PostgreSQL started by Testcontainers: what is stored, the row locks of
 * simultaneous reservations and the constraints of the tables.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class InventoryStorageTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-10-05T10:30:00Z");

    @Autowired
    private InventoryService inventory;

    @Autowired
    private StockRepository stock;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reservationCommitAndRestockAreStoredInTheDatabase() {
        UUID variantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventory.setStock(variantId, 10);

        inventory.reserve(orderId, List.of(new StockLine(variantId, 3)), EXPIRES_AT);
        assertStock(variantId, 10, 3);
        assertThat(reservations.findByOrderId(orderId)).singleElement().satisfies(reservation -> {
            assertThat(reservation.variantId()).isEqualTo(variantId);
            assertThat(reservation.quantity()).isEqualTo(3);
            assertThat(reservation.expiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        });

        inventory.commit(orderId);
        assertStock(variantId, 7, 0);
        assertThat(reservations.findByOrderId(orderId)).extracting(StockReservation::status)
                .containsExactly(ReservationStatus.COMMITTED);

        inventory.restock(orderId);
        assertStock(variantId, 10, 0);
        assertThat(reservations.findByOrderId(orderId)).extracting(StockReservation::status)
                .containsExactly(ReservationStatus.RESTOCKED);
    }

    @Test
    void releasedReservationReturnsTheGoodsAndCannotBeCommittedLater() {
        UUID variantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventory.setStock(variantId, 4);
        inventory.reserve(orderId, List.of(new StockLine(variantId, 4)), EXPIRES_AT);

        inventory.release(orderId);
        inventory.commit(orderId); // nothing is active any more

        assertStock(variantId, 4, 0);
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(4);
    }

    @Test
    void stockOfSeveralVariantsIsReadWithOneQuery() {
        UUID inStock = UUID.randomUUID();
        UUID partlyReserved = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        inventory.setStock(inStock, 7);
        inventory.setStock(partlyReserved, 5);
        inventory.reserve(UUID.randomUUID(), List.of(new StockLine(partlyReserved, 2)), EXPIRES_AT);

        assertThat(inventory.availableQuantities(List.of(inStock, partlyReserved, unknown)))
                .containsOnly(Map.entry(inStock, 7), Map.entry(partlyReserved, 3), Map.entry(unknown, 0));
        assertThat(inventory.availableQuantities(List.of())).isEmpty();
    }

    /** Goal "Provide accurate stock information": the last items are sold once, whatever the timing. */
    @Test
    void customersWhoReserveAtTheSameMomentGetNoMoreThanIsInStock() throws Exception {
        UUID variantId = UUID.randomUUID();
        inventory.setStock(variantId, 5);
        List<Callable<Boolean>> customers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            customers.add(() -> {
                try {
                    inventory.reserve(UUID.randomUUID(), List.of(new StockLine(variantId, 1)), EXPIRES_AT);
                    return true;
                } catch (OutOfStockException e) {
                    return false;
                }
            });
        }

        List<Boolean> reserved = runTogether(customers);

        assertThat(reserved).filteredOn(Boolean::booleanValue).hasSize(5);
        assertStock(variantId, 5, 5);
    }

    /** Stock rows are always locked in the order of variant ids, so two orders never wait for each other. */
    @Test
    void ordersWithTheSameVariantsInDifferentOrderDoNotBlockEachOther() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        inventory.setStock(first, 100);
        inventory.setStock(second, 100);
        List<Callable<Boolean>> customers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<StockLine> lines = i % 2 == 0
                    ? List.of(new StockLine(first, 1), new StockLine(second, 2))
                    : List.of(new StockLine(second, 2), new StockLine(first, 1));
            customers.add(() -> {
                inventory.reserve(UUID.randomUUID(), lines, EXPIRES_AT);
                return true;
            });
        }

        assertThat(runTogether(customers)).hasSize(20).containsOnly(true);
        assertStock(first, 100, 20);
        assertStock(second, 100, 40);
    }

    @Test
    void quantityOnHandCannotBeSetBelowTheReservedOne() {
        UUID variantId = UUID.randomUUID();
        inventory.setStock(variantId, 10);
        inventory.reserve(UUID.randomUUID(), List.of(new StockLine(variantId, 6)), EXPIRES_AT);

        assertThatThrownBy(() -> inventory.setStock(variantId, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.setStock(variantId, -1)).isInstanceOf(IllegalArgumentException.class);

        assertStock(variantId, 10, 6);
    }

    /** The tables protect the numbers even from a change that bypasses the application. */
    @Test
    void databaseKeepsTheReservedQuantityWithinTheQuantityOnHand() {
        UUID variantId = UUID.randomUUID();
        inventory.setStock(variantId, 3);

        assertThatThrownBy(() -> jdbc.update("UPDATE inventory.stock_items SET reserved = 4 WHERE variant_id = ?", variantId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory.stock_items SET on_hand = -1 WHERE variant_id = ?", variantId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventory.stock_reservations (id, order_id, variant_id, quantity, status)
                VALUES (?, ?, ?, 0, 'ACTIVE')""", UUID.randomUUID(), UUID.randomUUID(), variantId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertStock(variantId, 3, 0);
    }

    private void assertStock(UUID variantId, int onHand, int reserved) {
        StockItem item = stock.findByVariantId(variantId).orElseThrow();
        assertThat(item.onHand()).as("on hand").isEqualTo(onHand);
        assertThat(item.reserved()).as("reserved").isEqualTo(reserved);
    }

    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        List<T> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            for (Future<T> result : executor.invokeAll(tasks)) {
                results.add(result.get());
            }
        }
        return results;
    }
}
