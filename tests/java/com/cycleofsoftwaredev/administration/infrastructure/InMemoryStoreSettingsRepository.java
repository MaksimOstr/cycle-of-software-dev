package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.StoreSettings;
import com.cycleofsoftwaredev.administration.domain.StoreSettingsRepository;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryStoreSettingsRepository implements StoreSettingsRepository {

    private volatile StoreSettings settings = StoreSettings.defaults();

    @Override
    public StoreSettings load() {
        return settings;
    }

    @Override
    public void save(StoreSettings settings) {
        this.settings = settings;
    }
}
