package com.cycleofsoftwaredev.administration.application;

import com.cycleofsoftwaredev.ordering.api.SalesStatisticsApi;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Use case "Sales dashboard". Uses only the narrow {@link SalesStatisticsApi} of the Ordering module. */
@Service
public class SalesReportService {

    private final SalesStatisticsApi statistics;

    public SalesReportService(SalesStatisticsApi statistics) {
        this.statistics = statistics;
    }

    public SalesReport report(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("The start of the period must be before its end");
        }
        Duration length = Duration.between(from, to);
        return new SalesReport(from, to, statistics.salesSummary(from, to), statistics.salesSummary(from.minus(length), from));
    }
}
