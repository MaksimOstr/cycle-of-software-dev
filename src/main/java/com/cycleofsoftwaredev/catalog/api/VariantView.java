package com.cycleofsoftwaredev.catalog.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.UUID;

/** Data of a product variant that other modules may use. */
public record VariantView(UUID variantId, UUID productId, String sku, String productName, String variantName,
                          Money price, boolean available) {
}
