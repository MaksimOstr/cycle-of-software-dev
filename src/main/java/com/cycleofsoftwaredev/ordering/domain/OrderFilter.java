package com.cycleofsoftwaredev.ordering.domain;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
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

    /** The text to look for in lower case, or null if the search field is empty. */
    public String text() {
        return search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Digits of the searched phone number, or null if the text does not look like a phone. Phone numbers are
     * compared by digits, so "+380 (50) 123-45-67" is found by "050 123".
     */
    public String phoneDigits() {
        String text = text();
        return text != null && text.matches("\\+?[0-9 ()-]{3,}") ? digits(text) : null;
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
        String text = text();
        if (text == null) {
            return true;
        }
        String phoneDigits = phoneDigits();
        return order.number().toLowerCase(Locale.ROOT).contains(text)
                || order.contact().email().toLowerCase(Locale.ROOT).contains(text)
                || phoneDigits != null && digits(order.contact().phone()).contains(phoneDigits);
    }

    private static String digits(String value) {
        return value.replaceAll("\\D", "");
    }
}
