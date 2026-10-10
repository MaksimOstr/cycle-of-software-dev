package com.cycleofsoftwaredev.payment.domain;

import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.api.PaymentSession;

/**
 * Port to the payment provider for taking payments. Refunds are a separate interface
 * ({@link RefundGateway}), so an adapter is not forced to implement operations it does not support
 * (Interface Segregation principle).
 */
public interface PaymentGateway {

    PaymentSession createCheckoutSession(PaymentRequest request);

    void expireCheckoutSession(String sessionId);
}
