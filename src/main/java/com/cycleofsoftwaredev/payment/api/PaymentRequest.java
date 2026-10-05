package com.cycleofsoftwaredev.payment.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

public record PaymentRequest(UUID orderId, String orderNumber, Money amount) {

    public PaymentRequest {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(orderNumber);
        Objects.requireNonNull(amount);
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }
}
