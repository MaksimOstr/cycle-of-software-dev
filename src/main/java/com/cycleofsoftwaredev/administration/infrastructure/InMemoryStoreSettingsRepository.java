package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.StoreSettings;
import com.cycleofsoftwaredev.administration.domain.StoreSettingsRepository;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
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
