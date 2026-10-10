package com.cycleofsoftwaredev.delivery.application;

import com.cycleofsoftwaredev.delivery.api.DeliveryApi;
import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.delivery.domain.DeliveryCostPolicy;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Use case "Delivery methods". Receives all {@link DeliveryCostPolicy} beans, so supporting a new delivery
 * method requires only a new policy class and no change here (Open/Closed principle).
 */
@Service
public class DeliveryService implements DeliveryApi {

    private final Map<DeliveryMethod, DeliveryCostPolicy> policies = new EnumMap<>(DeliveryMethod.class);

    public DeliveryService(List<DeliveryCostPolicy> policies) {
        for (DeliveryCostPolicy policy : policies) {
            if (this.policies.put(policy.method(), policy) != null) {
                throw new IllegalStateException("Two cost policies for delivery method " + policy.method());
            }
        }
    }

    @Override
    public Money calculateCost(DeliveryMethod method, Money orderSubtotal) {
        DeliveryCostPolicy policy = policies.get(method);
        if (policy == null) {
            throw new BusinessRuleException("Delivery method " + method + " is not available");
        }
        return policy.calculate(orderSubtotal);
    }

    public Set<DeliveryMethod> availableMethods() {
        return policies.keySet();
    }
}
