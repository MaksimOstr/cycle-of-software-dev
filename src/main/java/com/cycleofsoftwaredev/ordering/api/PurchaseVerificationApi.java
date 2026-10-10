package com.cycleofsoftwaredev.ordering.api;

import java.util.UUID;

/**
 * Narrow interface for the Reviews module: only customers who received a product may review it.
 * Kept separate from {@link SalesStatisticsApi} so that each client depends only on what it uses
 * (Interface Segregation principle).
 */
public interface PurchaseVerificationApi {

    boolean hasReceivedProduct(UUID customerId, UUID productId);
}
