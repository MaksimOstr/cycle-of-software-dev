package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        payments.put(payment.id(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return payments.values().stream().filter(payment -> payment.orderId().equals(orderId)).findFirst();
    }

    @Override
    public Optional<Payment> findByProviderSessionId(String providerSessionId) {
        return payments.values().stream()
                .filter(payment -> payment.providerSessionId().equals(providerSessionId))
                .findFirst();
    }
}
