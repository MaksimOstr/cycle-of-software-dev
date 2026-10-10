package com.cycleofsoftwaredev.delivery.application;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.api.SettingsApi;
import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.shared.domain.Money;
import org.springframework.stereotype.Component;

@Component
public class NovaPoshtaCostPolicy extends PaidDeliveryCostPolicy {

    public NovaPoshtaCostPolicy(SettingsApi settings) {
        super(settings);
    }

    @Override
    public DeliveryMethod method() {
        return DeliveryMethod.NOVA_POSHTA;
    }

    @Override
    protected Money price(DeliverySettings settings) {
        return settings.novaPoshtaPrice();
    }
}
