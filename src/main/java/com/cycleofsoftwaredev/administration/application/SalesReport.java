package com.cycleofsoftwaredev.administration.application;

import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import java.time.Instant;

/** Sales of a period compared with the previous period of the same length. */
public record SalesReport(Instant from, Instant to, SalesSummary current, SalesSummary previous) {
}
