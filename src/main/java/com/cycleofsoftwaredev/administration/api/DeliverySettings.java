package com.cycleofsoftwaredev.administration.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.Objects;

/** Delivery prices configured by the administrator ("Store settings" requirement). */
public record DeliverySettings(Money courierPrice, Money novaPoshtaPrice, Money freeShippingThreshold) {

    public DeliverySettings {
        Objects.requireNonNull(courierPrice);
        Objects.requireNonNull(novaPoshtaPrice);
        Objects.requireNonNull(freeShippingThreshold);
        if (courierPrice.isNegative() || novaPoshtaPrice.isNegative() || freeShippingThreshold.isNegative()) {
            throw new IllegalArgumentException("Delivery prices must not be negative");
        }
    }
}
