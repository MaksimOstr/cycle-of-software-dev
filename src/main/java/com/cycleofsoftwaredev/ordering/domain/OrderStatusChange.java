package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;

/** Entry of the order status history: who changed the status, when and why. */
@Embeddable
public record OrderStatusChange(@Enumerated(EnumType.STRING) OrderStatus fromStatus,
                                @Enumerated(EnumType.STRING) OrderStatus toStatus, UUID changedBy, Instant changedAt,
                                @Column(name = "note", length = 500) String comment) {
}
