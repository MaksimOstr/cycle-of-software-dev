package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentRepository;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        payments.put(payment.id(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return payments.values().stream()
                .filter(payment -> payment.orderId().equals(orderId))
                .max(Comparator.comparing(Payment::createdAt));
    }

    @Override
    public Optional<Payment> findBySessionId(String providerSessionId) {
        return payments.values().stream()
                .filter(payment -> payment.providerSessionId().equals(providerSessionId))
                .max(Comparator.comparing(Payment::createdAt));
    }
}
