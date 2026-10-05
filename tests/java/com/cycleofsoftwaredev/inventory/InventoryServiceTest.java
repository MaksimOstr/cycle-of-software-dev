package com.cycleofsoftwaredev.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.inventory.api.StockLine;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.inventory.infrastructure.InMemoryReservationRepository;
import com.cycleofsoftwaredev.inventory.infrastructure.InMemoryStockRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private final InMemoryStockRepository stock = new InMemoryStockRepository();
    private final InventoryService inventory = new InventoryService(stock, new InMemoryReservationRepository());
    private final UUID phone = UUID.randomUUID();
    private final UUID hoodie = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inventory.setStock(phone, 5);
        inventory.setStock(hoodie, 1);
    }

    @Test
    void reservationReducesAvailableQuantity() {
        inventory.reserve(order, List.of(new StockLine(phone, 2)), null);

        assertThat(inventory.availableQuantity(phone)).isEqualTo(3);
        assertThat(stock.findByVariantId(phone).orElseThrow().onHand()).isEqualTo(5);
    }

    @Test
    void reservesAllLinesOrNone() {
        assertThatThrownBy(() -> inventory.reserve(order, List.of(new StockLine(phone, 1), new StockLine(hoodie, 2)), null))
                .isInstanceOf(OutOfStockException.class)
                .hasMessageContaining("Only 1");

        assertThat(inventory.availableQuantity(phone)).isEqualTo(5);
        assertThat(inventory.availableQuantity(hoodie)).isEqualTo(1);
    }

    @Test
    void commitTakesGoodsFromStock() {
        inventory.reserve(order, List.of(new StockLine(phone, 2)), null);
        inventory.commit(order);

        assertThat(stock.findByVariantId(phone).orElseThrow().onHand()).isEqualTo(3);
        assertThat(inventory.availableQuantity(phone)).isEqualTo(3);
    }

    @Test
    void releaseReturnsReservedGoods() {
        inventory.reserve(order, List.of(new StockLine(phone, 2)), null);
        inventory.release(order);

        assertThat(inventory.availableQuantity(phone)).isEqualTo(5);
    }

    @Test
    void restockReturnsCommittedGoods() {
        inventory.reserve(order, List.of(new StockLine(phone, 2)), null);
        inventory.commit(order);
        inventory.restock(order);

        assertThat(stock.findByVariantId(phone).orElseThrow().onHand()).isEqualTo(5);
        assertThat(inventory.availableQuantity(phone)).isEqualTo(5);
    }

    @Test
    void unknownVariantHasNoStock() {
        assertThat(inventory.availableQuantity(UUID.randomUUID())).isZero();
    }
}
