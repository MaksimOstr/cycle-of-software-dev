package com.cycleofsoftwaredev.notification.application;

import com.cycleofsoftwaredev.notification.domain.EmailComposer;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import com.cycleofsoftwaredev.ordering.api.OrderStatusChanged;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Requirement "Order status emails" (entry actions of the order state machine). */
@Component
public class OrderStatusEmailComposer implements EmailComposer<OrderStatusChanged> {

    @Override
    public Class<OrderStatusChanged> eventType() {
        return OrderStatusChanged.class;
    }

    @Override
    public Optional<EmailMessage> compose(OrderStatusChanged event) {
        String body = switch (event.to()) {
            case PAID -> "Payment for order " + event.orderNumber() + " has been received.";
            case SHIPPED -> "Order " + event.orderNumber() + " has been shipped. Tracking number: " + event.trackingNumber() + ".";
            case DELIVERED -> "Order " + event.orderNumber() + " has been delivered. Thank you for shopping with us!";
            case CANCELLED -> "Order " + event.orderNumber() + " has been cancelled.";
            default -> null;
        };
        if (body == null) {
            return Optional.empty();
        }
        String subject = "Order " + event.orderNumber() + ": " + event.to().name().toLowerCase(Locale.ROOT);
        return Optional.of(new EmailMessage(event.customerEmail(), subject, body));
    }
}
