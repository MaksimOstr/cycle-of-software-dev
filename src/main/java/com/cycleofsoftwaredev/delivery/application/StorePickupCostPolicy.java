package com.cycleofsoftwaredev.delivery.application;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.delivery.domain.DeliveryCostPolicy;
import com.cycleofsoftwaredev.shared.domain.Money;
import org.springframework.stereotype.Component;

/** Pickup from the store is always free. */
@Component
public class StorePickupCostPolicy implements DeliveryCostPolicy {

    @Override
    public DeliveryMethod method() {
        return DeliveryMethod.STORE_PICKUP;
    }

    @Override
    public Money calculate(Money orderSubtotal) {
        return Money.zero();
    }
}
