package com.cycleofsoftwaredev.support;

import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.api.PaymentSession;
import com.cycleofsoftwaredev.payment.domain.PaymentGateway;
import com.cycleofsoftwaredev.payment.domain.RefundGateway;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.ArrayList;
import java.util.List;

/** Fake payment provider that records what the Payment module asked it to do. */
public class RecordingPaymentProvider implements PaymentGateway, RefundGateway {

    private final List<String> expiredSessions = new ArrayList<>();
    private final List<String> refundedSessions = new ArrayList<>();
    private int sessionCounter;

    @Override
    public PaymentSession createCheckoutSession(PaymentRequest request) {
        String sessionId = "cs_test_" + (++sessionCounter);
        return new PaymentSession(sessionId, "https://pay.test/" + sessionId);
    }

    @Override
    public void expireCheckoutSession(String sessionId) {
        expiredSessions.add(sessionId);
    }

    @Override
    public void refund(String sessionId, Money amount) {
        refundedSessions.add(sessionId);
    }

    public List<String> expiredSessions() {
        return expiredSessions;
    }

    public List<String> refundedSessions() {
        return refundedSessions;
    }
}
