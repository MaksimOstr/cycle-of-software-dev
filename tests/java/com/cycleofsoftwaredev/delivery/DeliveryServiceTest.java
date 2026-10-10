package com.cycleofsoftwaredev.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.administration.application.SettingsService;
import com.cycleofsoftwaredev.administration.infrastructure.InMemoryStoreSettingsRepository;
import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.delivery.application.CourierCostPolicy;
import com.cycleofsoftwaredev.delivery.application.DeliveryService;
import com.cycleofsoftwaredev.delivery.application.NovaPoshtaCostPolicy;
import com.cycleofsoftwaredev.delivery.application.StorePickupCostPolicy;
import com.cycleofsoftwaredev.delivery.domain.DeliveryCostPolicy;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryServiceTest {

    private final SettingsService settings = new SettingsService(new InMemoryStoreSettingsRepository());

    @Test
    void calculatesCostFromStoreSettings() {
        DeliveryService delivery = new DeliveryService(List.of(new CourierCostPolicy(settings),
                new NovaPoshtaCostPolicy(settings), new StorePickupCostPolicy()));

        assertThat(delivery.calculateCost(DeliveryMethod.COURIER, Money.of("500"))).isEqualTo(Money.of("120"));
        assertThat(delivery.calculateCost(DeliveryMethod.NOVA_POSHTA, Money.of("500"))).isEqualTo(Money.of("80"));
        assertThat(delivery.calculateCost(DeliveryMethod.STORE_PICKUP, Money.of("500"))).isEqualTo(Money.zero());
        assertThat(delivery.calculateCost(DeliveryMethod.NOVA_POSHTA, Money.of("2000"))).isEqualTo(Money.zero());
    }

    @Test
    void newDeliveryMethodIsAddedWithoutChangingTheService() {
        DeliveryCostPolicy flatCourier = new DeliveryCostPolicy() {
            @Override
            public DeliveryMethod method() {
                return DeliveryMethod.COURIER;
            }

            @Override
            public Money calculate(Money orderSubtotal) {
                return Money.of("50");
            }
        };
        DeliveryService delivery = new DeliveryService(List.of(flatCourier));

        assertThat(delivery.calculateCost(DeliveryMethod.COURIER, Money.of("10"))).isEqualTo(Money.of("50"));
        assertThatThrownBy(() -> delivery.calculateCost(DeliveryMethod.NOVA_POSHTA, Money.of("10")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
