package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import java.util.UUID;

/** Data entered by the customer on the checkout page. {@code customerId} is null for guests. */
public record PlaceOrderCommand(UUID cartId, UUID customerId, ContactInfo contact, DeliveryMethod deliveryMethod,
                                String deliveryAddress, PaymentMethod paymentMethod) {
}
