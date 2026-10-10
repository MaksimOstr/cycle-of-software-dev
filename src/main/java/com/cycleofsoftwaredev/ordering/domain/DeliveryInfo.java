package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import java.util.Objects;

/** How and where the order is delivered. For Nova Poshta the address is the branch. */
public record DeliveryInfo(DeliveryMethod method, String address, String trackingNumber) {

    public DeliveryInfo {
        Objects.requireNonNull(method, "Delivery method is required");
        if (method != DeliveryMethod.STORE_PICKUP && (address == null || address.isBlank())) {
            throw new IllegalArgumentException("Delivery address is required for " + method);
        }
    }

    public static DeliveryInfo of(DeliveryMethod method, String address) {
        return new DeliveryInfo(method, address, null);
    }

    public DeliveryInfo withTrackingNumber(String newTrackingNumber) {
        return new DeliveryInfo(method, address, newTrackingNumber);
    }
}
