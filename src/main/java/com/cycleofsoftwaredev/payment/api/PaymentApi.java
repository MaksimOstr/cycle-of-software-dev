package com.cycleofsoftwaredev.payment.api;

import java.util.Optional;
import java.util.UUID;

/** Public interface of the Payment module: used by Ordering. */
public interface PaymentApi {

    PaymentSession startPayment(PaymentRequest request);

    Optional<PaymentSession> findSession(UUID orderId);

    /** The order was cancelled before payment: the payment page must stop accepting the payment. */
    void cancelPayment(UUID orderId);

    /** Returns the money of a paid order to the customer. */
    void refund(UUID orderId);
}
