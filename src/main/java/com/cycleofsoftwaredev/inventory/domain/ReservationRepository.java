package com.cycleofsoftwaredev.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository {

    StockReservation save(StockReservation reservation);

    List<StockReservation> findByOrderId(UUID orderId);
}
