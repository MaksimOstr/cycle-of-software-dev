package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of payments. */
public interface JpaPaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepository {
}
