package com.cycleofsoftwaredev.delivery.api;

import com.cycleofsoftwaredev.shared.domain.Money;

/** Public interface of the Delivery module: used by Ordering during checkout. */
public interface DeliveryApi {

    Money calculateCost(DeliveryMethod method, Money orderSubtotal);
}
