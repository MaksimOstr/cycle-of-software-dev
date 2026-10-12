package com.cycleofsoftwaredev.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentRepository;
import com.cycleofsoftwaredev.payment.domain.PaymentStatus;
import com.cycleofsoftwaredev.payment.domain.ProcessedWebhookEventRepository;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** Payment module with the real PostgreSQL started by Testcontainers. */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class PaymentStorageTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-05T10:00:00Z");

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private ProcessedWebhookEventRepository processedEvents;

    @Test
    void paymentIsFoundByOrderAndByTheSessionOfTheProvider() {
        Payment payment = payments.save(payment(UUID.randomUUID()));

        Payment byOrder = payments.findByOrderId(payment.orderId()).orElseThrow();
        Payment bySession = payments.findByProviderSessionId(payment.providerSessionId()).orElseThrow();

        assertThat(byOrder.id()).isEqualTo(payment.id()).isEqualTo(bySession.id());
        assertThat(byOrder.amount()).isEqualTo(Money.of("1880.00"));
        assertThat(byOrder.paymentUrl()).isEqualTo(payment.paymentUrl());
        assertThat(byOrder.createdAt()).isEqualTo(CREATED_AT);
        assertThat(byOrder.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.findByOrderId(UUID.randomUUID())).isEmpty();
        assertThat(payments.findByProviderSessionId("cs_unknown")).isEmpty();
    }

    @Test
    void statusOfThePaymentIsStored() {
        Payment payment = payments.save(payment(UUID.randomUUID()));

        Payment loaded = payments.findByOrderId(payment.orderId()).orElseThrow();
        loaded.markSucceeded();
        loaded.markRefunded();
        payments.save(loaded);

        assertThat(payments.findByOrderId(payment.orderId()).orElseThrow().status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    /** NFR "No card data stored": the table keeps only the session of the provider, the link and the amount. */
    @Test
    void orderHasOnlyOnePayment() {
        UUID orderId = UUID.randomUUID();
        payments.save(payment(orderId));

        assertThatThrownBy(() -> payments.save(payment(orderId))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void providerEventIsAcceptedOnlyTheFirstTime() {
        String eventId = "evt_" + UUID.randomUUID();

        assertThat(processedEvents.markProcessed(eventId)).isTrue();
        assertThat(processedEvents.markProcessed(eventId)).isFalse();
        assertThat(processedEvents.markProcessed("evt_" + UUID.randomUUID())).isTrue();
    }

    /** NFR "Order and payment integrity": the provider may deliver one notification several times at once. */
    @Test
    void sameEventDeliveredAtTheSameMomentIsAcceptedOnce() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        List<Callable<Boolean>> deliveries = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            deliveries.add(() -> processedEvents.markProcessed(eventId));
        }

        List<Boolean> accepted = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(deliveries.size())) {
            for (Future<Boolean> delivery : executor.invokeAll(deliveries)) {
                accepted.add(delivery.get());
            }
        }

        assertThat(accepted).filteredOn(Boolean::booleanValue).hasSize(1);
    }

    private static Payment payment(UUID orderId) {
        String sessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
        return new Payment(UUID.randomUUID(), orderId, sessionId, "https://checkout.example.com/pay/" + sessionId,
                Money.of("1880.00"), CREATED_AT);
    }
}
