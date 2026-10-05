package com.cycleofsoftwaredev.administration.api;

/** Store settings needed by other modules (Delivery reads delivery prices and the free-shipping threshold). */
public interface SettingsApi {

    DeliverySettings deliverySettings();
}
