package com.cycleofsoftwaredev.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.administration.application.SalesReport;
import com.cycleofsoftwaredev.administration.application.SalesReportService;
import com.cycleofsoftwaredev.ordering.api.SalesStatisticsApi;
import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SalesReportServiceTest {

    private static final Instant SEPTEMBER = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant OCTOBER = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void comparesPeriodWithPreviousPeriodOfSameLength() {
        SalesStatisticsApi statistics = (from, to) -> from.equals(SEPTEMBER)
                ? new SalesSummary(10, Money.of("15000"))
                : new SalesSummary(8, Money.of("12000"));

        SalesReport report = new SalesReportService(statistics).report(SEPTEMBER, OCTOBER);

        assertThat(report.current().ordersCount()).isEqualTo(10);
        assertThat(report.current().averageOrderValue()).isEqualTo(Money.of("1500"));
        assertThat(report.previous().revenue()).isEqualTo(Money.of("12000"));
    }

    @Test
    void rejectsEmptyPeriod() {
        SalesReportService service = new SalesReportService((from, to) -> new SalesSummary(0, Money.zero()));

        assertThatThrownBy(() -> service.report(OCTOBER, SEPTEMBER)).isInstanceOf(IllegalArgumentException.class);
    }
}
