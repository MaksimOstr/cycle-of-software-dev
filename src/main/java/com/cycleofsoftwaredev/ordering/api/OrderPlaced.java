package com.cycleofsoftwaredev.ordering.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(UUID orderId, String orderNumber, String customerEmail, String customerName, Money total,
                          Instant occurredAt) implements DomainEvent {
}
