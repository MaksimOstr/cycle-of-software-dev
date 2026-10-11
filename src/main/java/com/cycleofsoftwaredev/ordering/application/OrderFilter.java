package com.cycleofsoftwaredev.ordering.application;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import java.time.Instant;
import java.util.Locale;

/**
 * Filters of the manager's order list (requirement "Order list"). A filter that is not set (null) accepts
 * every order. The text is searched in the order number, the customer email and the phone number.
 */
public record OrderFilter(OrderStatus status, PaymentMethod paymentMethod, DeliveryMethod deliveryMethod,
                          Instant from, Instant to, String search) {

    public static OrderFilter all() {
        return new OrderFilter(null, null, null, null, null, null);
    }

    public boolean matches(Order order) {
        return (status == null || order.status() == status)
                && (paymentMethod == null || order.paymentMethod() == paymentMethod)
                && (deliveryMethod == null || order.delivery().method() == deliveryMethod)
                && (from == null || !order.createdAt().isBefore(from))
                && (to == null || order.createdAt().isBefore(to))
                && matchesText(order);
    }

    private boolean matchesText(Order order) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String text = search.trim().toLowerCase(Locale.ROOT);
        return order.number().toLowerCase(Locale.ROOT).contains(text)
                || order.contact().email().toLowerCase(Locale.ROOT).contains(text)
                || looksLikePhone(text) && digits(order.contact().phone()).contains(digits(text));
    }

    private static boolean looksLikePhone(String text) {
        return text.matches("\\+?[0-9 ()-]{3,}");
    }

    /** Phone numbers are compared by digits, so "+380 (50) 123-45-67" is found by "0501234567". */
    private static String digits(String value) {
        return value.replaceAll("\\D", "");
    }
}
