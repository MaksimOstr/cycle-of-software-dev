package com.cycleofsoftwaredev.catalog.api;

import java.util.Optional;
import java.util.UUID;

/** Public interface of the Catalog module: used by Cart to get current prices and variant data. */
public interface CatalogApi {

    Optional<VariantView> findVariant(UUID variantId);
}
