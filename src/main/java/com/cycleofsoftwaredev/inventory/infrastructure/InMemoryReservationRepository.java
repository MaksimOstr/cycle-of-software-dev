package com.cycleofsoftwaredev.inventory.infrastructure;

import com.cycleofsoftwaredev.inventory.domain.ReservationRepository;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<UUID, StockReservation> reservations = new ConcurrentHashMap<>();

    @Override
    public void save(StockReservation reservation) {
        reservations.put(reservation.id(), reservation);
    }

    @Override
    public List<StockReservation> findByOrderId(UUID orderId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.orderId().equals(orderId))
                .toList();
    }
}
