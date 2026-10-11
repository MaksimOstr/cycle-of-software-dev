package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.StoreSettings;
import com.cycleofsoftwaredev.administration.domain.StoreSettingsRepository;
import org.springframework.stereotype.Repository;

/** PostgreSQL storage of the store settings: one row, created by the database changelog. */
@Repository
public class JpaStoreSettingsRepository implements StoreSettingsRepository {

    private final JpaStoreSettingsRows rows;

    JpaStoreSettingsRepository(JpaStoreSettingsRows rows) {
        this.rows = rows;
    }

    @Override
    public StoreSettings load() {
        return rows.findById(StoreSettings.SINGLE_ROW_ID).orElseGet(StoreSettings::defaults);
    }

    @Override
    public void save(StoreSettings settings) {
        rows.save(settings);
    }
}
