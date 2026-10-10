package com.cycleofsoftwaredev.ordering.api;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record SalesSummary(long ordersCount, Money revenue) {

    public Money averageOrderValue() {
        if (ordersCount == 0) {
            return Money.zero();
        }
        return Money.of(revenue.amount().divide(BigDecimal.valueOf(ordersCount), 2, RoundingMode.HALF_UP));
    }
}
