package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.UUID;

/** Result of checkout. {@code paymentUrl} is set only for online card payment. */
public record CheckoutResult(UUID orderId, String orderNumber, OrderStatus status, Money total, String paymentUrl) {
}
