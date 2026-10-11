package com.cycleofsoftwaredev.inventory.infrastructure;

import com.cycleofsoftwaredev.inventory.domain.ReservationRepository;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of stock reservations. */
public interface JpaReservationRepository extends JpaRepository<StockReservation, UUID>, ReservationRepository {
}
