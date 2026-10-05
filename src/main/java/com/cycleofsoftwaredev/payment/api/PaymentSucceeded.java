package com.cycleofsoftwaredev.payment.api;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceeded(UUID orderId, UUID paymentId, Instant occurredAt) implements DomainEvent {
}
