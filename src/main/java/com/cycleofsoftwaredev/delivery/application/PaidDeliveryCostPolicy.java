package com.cycleofsoftwaredev.delivery.application;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.api.SettingsApi;
import com.cycleofsoftwaredev.delivery.domain.DeliveryCostPolicy;
import com.cycleofsoftwaredev.shared.domain.Money;

/** Paid delivery that becomes free when the order subtotal reaches the free-shipping threshold. */
public abstract class PaidDeliveryCostPolicy implements DeliveryCostPolicy {

    private final SettingsApi settings;

    protected PaidDeliveryCostPolicy(SettingsApi settings) {
        this.settings = settings;
    }

    @Override
    public final Money calculate(Money orderSubtotal) {
        DeliverySettings current = settings.deliverySettings();
        if (orderSubtotal.isGreaterThanOrEqual(current.freeShippingThreshold())) {
            return Money.zero();
        }
        return price(current);
    }

    protected abstract Money price(DeliverySettings settings);
}
