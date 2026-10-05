package com.cycleofsoftwaredev.payment.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findBySessionId(String providerSessionId);
}
