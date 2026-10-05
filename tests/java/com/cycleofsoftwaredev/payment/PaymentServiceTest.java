package com.cycleofsoftwaredev.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.api.PaymentSession;
import com.cycleofsoftwaredev.payment.api.PaymentSucceeded;
import com.cycleofsoftwaredev.payment.application.PaymentService;
import com.cycleofsoftwaredev.payment.application.PaymentWebhookEvent;
import com.cycleofsoftwaredev.payment.domain.PaymentStatus;
import com.cycleofsoftwaredev.payment.infrastructure.InMemoryPaymentRepository;
import com.cycleofsoftwaredev.payment.infrastructure.InMemoryProcessedWebhookEventRepository;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.support.MutableClock;
import com.cycleofsoftwaredev.support.RecordingEventPublisher;
import com.cycleofsoftwaredev.support.RecordingPaymentProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    private final RecordingPaymentProvider provider = new RecordingPaymentProvider();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final PaymentService service = new PaymentService(payments, new InMemoryProcessedWebhookEventRepository(),
            provider, provider, events, new MutableClock(Instant.parse("2026-10-05T10:00:00Z")));
    private final UUID orderId = UUID.randomUUID();

    @Test
    void repeatedWebhookIsProcessedOnlyOnce() {
        PaymentSession session = service.startPayment(new PaymentRequest(orderId, "SS-1", Money.of("100")));
        PaymentWebhookEvent event = new PaymentWebhookEvent("evt_1", PaymentWebhookEvent.CHECKOUT_COMPLETED, session.sessionId());

        service.handleWebhook(event);
        service.handleWebhook(event);

        assertThat(events.eventsOfType(PaymentSucceeded.class)).singleElement()
                .extracting(PaymentSucceeded::orderId).isEqualTo(orderId);
        assertThat(payments.findByOrderId(orderId).orElseThrow().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void otherEventTypesAreIgnored() {
        PaymentSession session = service.startPayment(new PaymentRequest(orderId, "SS-1", Money.of("100")));

        service.handleWebhook(new PaymentWebhookEvent("evt_2", "payment_intent.created", session.sessionId()));

        assertThat(events.eventsOfType(PaymentSucceeded.class)).isEmpty();
    }

    @Test
    void unknownSessionIsRejected() {
        assertThatThrownBy(() -> service.handleWebhook(
                new PaymentWebhookEvent("evt_3", PaymentWebhookEvent.CHECKOUT_COMPLETED, "cs_unknown")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelExpiresSessionAndRefundReturnsMoney() {
        PaymentSession session = service.startPayment(new PaymentRequest(orderId, "SS-1", Money.of("100")));
        service.cancelPayment(orderId);
        assertThat(provider.expiredSessions()).containsExactly(session.sessionId());

        // the customer managed to pay just before the session expired
        service.handleWebhook(new PaymentWebhookEvent("evt_4", PaymentWebhookEvent.CHECKOUT_COMPLETED, session.sessionId()));
        service.refund(orderId);

        assertThat(provider.refundedSessions()).containsExactly(session.sessionId());
        assertThat(payments.findByOrderId(orderId).orElseThrow().status()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
