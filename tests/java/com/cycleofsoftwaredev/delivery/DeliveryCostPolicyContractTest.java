package com.cycleofsoftwaredev.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.api.SettingsApi;
import com.cycleofsoftwaredev.delivery.application.CourierCostPolicy;
import com.cycleofsoftwaredev.delivery.application.NovaPoshtaCostPolicy;
import com.cycleofsoftwaredev.delivery.application.StorePickupCostPolicy;
import com.cycleofsoftwaredev.delivery.domain.DeliveryCostPolicy;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Liskov substitution check: every implementation of {@link DeliveryCostPolicy} fulfils the same contract,
 * so {@code DeliveryService} works correctly with any of them.
 */
class DeliveryCostPolicyContractTest {

    private static final SettingsApi SETTINGS =
            () -> new DeliverySettings(Money.of("120"), Money.of("80"), Money.of("2000"));

    static Stream<DeliveryCostPolicy> policies() {
        return Stream.of(new CourierCostPolicy(SETTINGS), new NovaPoshtaCostPolicy(SETTINGS), new StorePickupCostPolicy());
    }

    @ParameterizedTest
    @MethodSource("policies")
    void costIsNeverNegative(DeliveryCostPolicy policy) {
        for (Money subtotal : List.of(Money.zero(), Money.of("1"), Money.of("1999.99"), Money.of("2000"), Money.of("100000"))) {
            assertThat(policy.calculate(subtotal)).isNotNull();
            assertThat(policy.calculate(subtotal).isNegative()).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("policies")
    void costDoesNotGrowWithSubtotal(DeliveryCostPolicy policy) {
        Money small = policy.calculate(Money.of("100"));
        Money large = policy.calculate(Money.of("5000"));

        assertThat(large.compareTo(small)).isLessThanOrEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("policies")
    void declaresDeliveryMethod(DeliveryCostPolicy policy) {
        assertThat(policy.method()).isNotNull();
    }
}
