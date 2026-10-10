package com.cycleofsoftwaredev.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsToTwoDecimalPlacesAndUsesHryvniaByDefault() {
        Money money = Money.of(new BigDecimal("10.005"));

        assertThat(money.amount()).isEqualByComparingTo("10.01");
        assertThat(money.currency()).isEqualTo(Money.UAH);
    }

    @Test
    void supportsArithmetic() {
        Money price = Money.of("199.99");

        assertThat(price.multiply(3)).isEqualTo(Money.of("599.97"));
        assertThat(price.add(Money.of("0.01"))).isEqualTo(Money.of("200.00"));
        assertThat(price.subtract(Money.of("99.99"))).isEqualTo(Money.of("100.00"));
        assertThat(Money.of("250.00").percent(10)).isEqualTo(Money.of("25.00"));
    }

    @Test
    void comparesAmounts() {
        assertThat(Money.of("100").isGreaterThanOrEqual(Money.of("100.00"))).isTrue();
        assertThat(Money.of("99.99").isGreaterThanOrEqual(Money.of("100"))).isFalse();
        assertThat(Money.zero().isZero()).isTrue();
        assertThat(Money.of("-1").isNegative()).isTrue();
    }

    @Test
    void rejectsDifferentCurrencies() {
        Money hryvnias = Money.of("10");
        Money euros = new Money(new BigDecimal("10"), "EUR");

        assertThatThrownBy(() -> hryvnias.add(euros)).isInstanceOf(IllegalArgumentException.class);
    }
}
