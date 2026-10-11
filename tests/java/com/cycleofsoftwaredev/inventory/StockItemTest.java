package com.cycleofsoftwaredev.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.inventory.api.StockLine;
import com.cycleofsoftwaredev.inventory.domain.ReservationStatus;
import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Arithmetic of the stock of one variant and the life cycle of a reservation. */
class StockItemTest {

    private final UUID variantId = UUID.randomUUID();

    @Test
    void reservedGoodsAreNotAvailableButStayOnHand() {
        StockItem item = new StockItem(variantId, 10);

        item.reserve(3);

        assertThat(item.onHand()).isEqualTo(10);
        assertThat(item.reserved()).isEqualTo(3);
        assertThat(item.available()).isEqualTo(7);
    }

    @Test
    void theLastItemsCanBeReservedButNotMore() {
        StockItem item = new StockItem(variantId, 5);
        item.reserve(5);

        assertThatThrownBy(() -> item.reserve(1))
                .isInstanceOfSatisfying(OutOfStockException.class, e -> {
                    assertThat(e.variantId()).isEqualTo(variantId);
                    assertThat(e.available()).isZero();
                    assertThat(e).hasMessageContaining("Only 0 item(s) left");
                });
        assertThat(item.reserved()).isEqualTo(5);
    }

    @Test
    void committedGoodsLeaveTheStock() {
        StockItem item = new StockItem(variantId, 10);
        item.reserve(4);

        item.commitReserved(4);

        assertThat(item.onHand()).isEqualTo(6);
        assertThat(item.reserved()).isZero();
        assertThat(item.available()).isEqualTo(6);
    }

    @Test
    void releasedGoodsBecomeAvailableAgain() {
        StockItem item = new StockItem(variantId, 10);
        item.reserve(4);

        item.releaseReserved(4);

        assertThat(item.onHand()).isEqualTo(10);
        assertThat(item.available()).isEqualTo(10);
    }

    @Test
    void returnedGoodsArePutBack() {
        StockItem item = new StockItem(variantId, 10);
        item.reserve(4);
        item.commitReserved(4);

        item.putBack(4);

        assertThat(item.onHand()).isEqualTo(10);
    }

    @Test
    void quantityOnHandCannotBeSetBelowWhatIsReservedForOrders() {
        StockItem item = new StockItem(variantId, 10);
        item.reserve(4);

        assertThatThrownBy(() -> item.setOnHand(3)).isInstanceOf(IllegalArgumentException.class);
        item.setOnHand(4);

        assertThat(item.available()).isZero();
    }

    /** Requirement "Stock management": variants at or below the threshold (5 by default) are highlighted. */
    @Test
    void lowStockIsReportedAtTheThreshold() {
        assertThat(new StockItem(variantId, 6).isLowStock()).isFalse();
        assertThat(new StockItem(variantId, 5).isLowStock()).isTrue();
        assertThat(new StockItem(variantId, 0).isLowStock()).isTrue();

        StockItem item = new StockItem(variantId, 6);
        item.reserve(1); // reserved goods do not count
        assertThat(item.isLowStock()).isTrue();
    }

    @Test
    void thresholdIsConfigurable() {
        StockItem item = new StockItem(variantId, 15);
        assertThat(item.lowStockThreshold()).isEqualTo(StockItem.DEFAULT_LOW_STOCK_THRESHOLD);

        item.setLowStockThreshold(20);

        assertThat(item.isLowStock()).isTrue();
        assertThatThrownBy(() -> item.setLowStockThreshold(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationIsCommittedAndThenRestockedOnReturn() {
        StockReservation reservation = reservation();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);

        reservation.markCommitted();
        reservation.markRestocked();

        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESTOCKED);
    }

    @Test
    void reservationCannotBeUsedTwice() {
        StockReservation released = reservation();
        released.markReleased();
        StockReservation committed = reservation();
        committed.markCommitted();

        assertThatThrownBy(released::markCommitted).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(released::markReleased).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(committed::markReleased).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> reservation().markRestocked()).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void stockLineNeedsAVariantAndAPositiveQuantity() {
        assertThatThrownBy(() -> new StockLine(variantId, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockLine(null, 1)).isInstanceOf(NullPointerException.class);
    }

    private StockReservation reservation() {
        return new StockReservation(UUID.randomUUID(), UUID.randomUUID(), variantId, 2,
                Instant.parse("2026-10-05T10:30:00Z"));
    }
}
