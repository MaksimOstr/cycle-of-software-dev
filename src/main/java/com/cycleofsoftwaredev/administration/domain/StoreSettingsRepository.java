package com.cycleofsoftwaredev.administration.domain;

public interface StoreSettingsRepository {

    StoreSettings load();

    void save(StoreSettings settings);
}
