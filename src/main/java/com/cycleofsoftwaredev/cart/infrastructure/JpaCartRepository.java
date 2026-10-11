package com.cycleofsoftwaredev.cart.infrastructure;

import com.cycleofsoftwaredev.cart.domain.Cart;
import com.cycleofsoftwaredev.cart.domain.CartRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL storage of carts with their items. */
public interface JpaCartRepository extends JpaRepository<Cart, UUID>, CartRepository {

    /** The items are removed by the database together with the cart (ON DELETE CASCADE). */
    @Override
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cart.carts WHERE customer_id IS NULL AND updated_at < :time", nativeQuery = true)
    int deleteGuestCartsNotChangedSince(@Param("time") Instant time);
}
