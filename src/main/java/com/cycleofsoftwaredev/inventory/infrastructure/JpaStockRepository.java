package com.cycleofsoftwaredev.inventory.infrastructure;

import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.inventory.domain.StockRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PostgreSQL storage of stock quantities. */
public interface JpaStockRepository extends JpaRepository<StockItem, UUID>, StockRepository {

    /** SELECT ... FOR UPDATE: other transactions wait until this one is finished. */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.variantId = :variantId")
    Optional<StockItem> lockByVariantId(@Param("variantId") UUID variantId);

    @Override
    @Query("select s.onHand - s.reserved from StockItem s where s.variantId = :variantId")
    Optional<Integer> findAvailableQuantity(@Param("variantId") UUID variantId);
}
