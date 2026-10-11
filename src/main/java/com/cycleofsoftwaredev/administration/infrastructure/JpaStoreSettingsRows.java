package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the table of store settings, used by {@link JpaStoreSettingsRepository}. */
interface JpaStoreSettingsRows extends JpaRepository<StoreSettings, Integer> {
}
