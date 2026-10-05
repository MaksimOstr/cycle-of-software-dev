package com.cycleofsoftwaredev.administration.domain;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.Objects;

/** Global settings of the store. */
public class StoreSettings {

    private String storeName;
    private DeliverySettings delivery;

    public StoreSettings(String storeName, DeliverySettings delivery) {
        rename(storeName);
        changeDelivery(delivery);
    }

    public static StoreSettings defaults() {
        return new StoreSettings("ShopSphere",
                new DeliverySettings(Money.of("120.00"), Money.of("80.00"), Money.of("2000.00")));
    }

    public void rename(String newStoreName) {
        if (newStoreName == null || newStoreName.isBlank()) {
            throw new IllegalArgumentException("Store name must not be blank");
        }
        this.storeName = newStoreName.trim();
    }

    public void changeDelivery(DeliverySettings newDelivery) {
        this.delivery = Objects.requireNonNull(newDelivery);
    }

    public String storeName() {
        return storeName;
    }

    public DeliverySettings delivery() {
        return delivery;
    }
}
