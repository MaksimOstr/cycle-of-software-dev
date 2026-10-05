package com.cycleofsoftwaredev.payment.infrastructure;

import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.api.PaymentSession;
import com.cycleofsoftwaredev.payment.domain.PaymentGateway;
import com.cycleofsoftwaredev.payment.domain.RefundGateway;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the Stripe adapter (work item SHOP-39) used in development and tests.
 * It creates fake sessions; a payment is confirmed by sending a webhook to /api/v1/payments/webhook.
 */
@Component
public class SimulatedPaymentProvider implements PaymentGateway, RefundGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProvider.class);

    @Override
    public PaymentSession createCheckoutSession(PaymentRequest request) {
        String sessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
        log.info("Created payment session {} for order {} ({})", sessionId, request.orderNumber(), request.amount());
        return new PaymentSession(sessionId, "https://checkout.example.com/pay/" + sessionId);
    }

    @Override
    public void expireCheckoutSession(String sessionId) {
        log.info("Expired payment session {}", sessionId);
    }

    @Override
    public void refund(String sessionId, Money amount) {
        log.info("Refunded {} for payment session {}", amount, sessionId);
    }
}
