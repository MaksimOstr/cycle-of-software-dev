package com.cycleofsoftwaredev.ordering.api;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record OrderStatusChanged(UUID orderId, String orderNumber, String customerEmail, OrderStatus from,
                                 OrderStatus to, String trackingNumber, Instant occurredAt) implements DomainEvent {
}
