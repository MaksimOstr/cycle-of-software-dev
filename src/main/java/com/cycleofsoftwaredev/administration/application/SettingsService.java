package com.cycleofsoftwaredev.administration.application;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.api.SettingsApi;
import com.cycleofsoftwaredev.administration.domain.StoreSettings;
import com.cycleofsoftwaredev.administration.domain.StoreSettingsRepository;
import org.springframework.stereotype.Service;

/** Use case "Store settings". */
@Service
public class SettingsService implements SettingsApi {

    private final StoreSettingsRepository settings;

    public SettingsService(StoreSettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public DeliverySettings deliverySettings() {
        return settings.load().delivery();
    }

    public StoreSettings currentSettings() {
        return settings.load();
    }

    public void updateDeliverySettings(DeliverySettings delivery) {
        StoreSettings current = settings.load();
        current.changeDelivery(delivery);
        settings.save(current);
    }
}
