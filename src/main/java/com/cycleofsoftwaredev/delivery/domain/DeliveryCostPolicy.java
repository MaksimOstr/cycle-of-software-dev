package com.cycleofsoftwaredev.delivery.domain;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.shared.domain.Money;

/**
 * Calculates the delivery cost for one delivery method (Open/Closed principle: a new method is added
 * as a new implementation, the existing classes are not changed).
 * <p>
 * Contract for every implementation (Liskov substitution): the result is never null and never negative,
 * and does not grow when the order subtotal grows.
 */
public interface DeliveryCostPolicy {

    DeliveryMethod method();

    Money calculate(Money orderSubtotal);
}
