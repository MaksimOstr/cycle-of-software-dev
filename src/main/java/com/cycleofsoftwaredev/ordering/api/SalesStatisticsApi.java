package com.cycleofsoftwaredev.ordering.api;

import java.time.Instant;

/** Narrow interface for the Administration and Analytics module (sales dashboard). */
public interface SalesStatisticsApi {

    /** Orders created in {@code [from, to)} that were not cancelled or returned. */
    SalesSummary salesSummary(Instant from, Instant to);
}
