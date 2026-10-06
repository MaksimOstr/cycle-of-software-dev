package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Entry of the order status history: who changed the status, when and why. */
public record OrderStatusChange(OrderStatus fromStatus, OrderStatus toStatus, UUID changedBy, Instant changedAt,
                                String comment) {
}
