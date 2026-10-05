package com.cycleofsoftwaredev.payment.api;

/** Session at the payment provider: the customer is redirected to {@code paymentUrl} to enter card data. */
public record PaymentSession(String sessionId, String paymentUrl) {
}
