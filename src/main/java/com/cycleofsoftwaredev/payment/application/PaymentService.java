package com.cycleofsoftwaredev.payment.application;

import com.cycleofsoftwaredev.payment.api.PaymentApi;
import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.api.PaymentSession;
import com.cycleofsoftwaredev.payment.api.PaymentSucceeded;
import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentGateway;
import com.cycleofsoftwaredev.payment.domain.PaymentRepository;
import com.cycleofsoftwaredev.payment.domain.PaymentStatus;
import com.cycleofsoftwaredev.payment.domain.ProcessedWebhookEventRepository;
import com.cycleofsoftwaredev.payment.domain.RefundGateway;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use case "Payment methods" (online card payment) and processing of payment provider webhooks. */
@Service
public class PaymentService implements PaymentApi {

    private final PaymentRepository payments;
    private final ProcessedWebhookEventRepository processedEvents;
    private final PaymentGateway paymentGateway;
    private final RefundGateway refundGateway;
    private final DomainEventPublisher events;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, ProcessedWebhookEventRepository processedEvents,
                          PaymentGateway paymentGateway, RefundGateway refundGateway,
                          DomainEventPublisher events, Clock clock) {
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.paymentGateway = paymentGateway;
        this.refundGateway = refundGateway;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public PaymentSession startPayment(PaymentRequest request) {
        PaymentSession session = paymentGateway.createCheckoutSession(request);
        payments.save(new Payment(UUID.randomUUID(), request.orderId(), session.sessionId(), session.paymentUrl(),
                request.amount(), clock.instant()));
        return session;
    }

    @Override
    public Optional<PaymentSession> findSession(UUID orderId) {
        return payments.findByOrderId(orderId)
                .map(payment -> new PaymentSession(payment.providerSessionId(), payment.paymentUrl()));
    }

    @Override
    public void cancelPayment(UUID orderId) {
        payments.findByOrderId(orderId)
                .filter(payment -> payment.status() == PaymentStatus.PENDING)
                .ifPresent(payment -> {
                    paymentGateway.expireCheckoutSession(payment.providerSessionId());
                    payment.markCancelled();
                    payments.save(payment);
                });
    }

    @Override
    public void refund(UUID orderId) {
        payments.findByOrderId(orderId)
                .filter(payment -> payment.status() == PaymentStatus.SUCCEEDED)
                .ifPresent(payment -> {
                    refundGateway.refund(payment.providerSessionId(), payment.amount());
                    payment.markRefunded();
                    payments.save(payment);
                });
    }

    /** Processes a provider notification once; repeated deliveries of the same event are ignored. */
    public void handleWebhook(PaymentWebhookEvent event) {
        if (!PaymentWebhookEvent.CHECKOUT_COMPLETED.equals(event.type())) {
            return;
        }
        Payment payment = payments.findBySessionId(event.sessionId())
                .orElseThrow(() -> new NotFoundException("Payment session", event.sessionId()));
        if (!processedEvents.markProcessed(event.eventId())) {
            return;
        }
        if (payment.status() != PaymentStatus.PENDING && payment.status() != PaymentStatus.CANCELLED) {
            return;
        }
        payment.markSucceeded();
        payments.save(payment);
        events.publish(new PaymentSucceeded(payment.orderId(), payment.id(), clock.instant()));
    }
}
