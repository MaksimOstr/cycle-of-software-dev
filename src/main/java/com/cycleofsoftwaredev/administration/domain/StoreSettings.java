package com.cycleofsoftwaredev.administration.domain;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/** Global settings of the store. */
@Entity
@Table(name = "store_settings", schema = "administration")
public class StoreSettings {

    /** The store has exactly one row of settings. */
    public static final int SINGLE_ROW_ID = 1;

    @Id
    private int id = SINGLE_ROW_ID;
    private String storeName;

    @Embedded
    private DeliverySettings delivery;

    protected StoreSettings() {
        // for JPA
    }

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
