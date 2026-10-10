package com.cycleofsoftwaredev.payment.application;

/** Notification from the payment provider, already parsed and with a verified signature. */
public record PaymentWebhookEvent(String eventId, String type, String sessionId) {

    public static final String CHECKOUT_COMPLETED = "checkout.session.completed";
}
