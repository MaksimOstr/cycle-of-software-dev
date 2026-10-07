package com.cycleofsoftwaredev.notification.application;

import com.cycleofsoftwaredev.notification.domain.EmailComposer;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import com.cycleofsoftwaredev.ordering.api.OrderPlaced;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Requirement "Order confirmation": email with the order number and total. */
@Component
public class OrderCreatedEmailComposer implements EmailComposer<OrderPlaced> {

    @Override
    public Class<OrderPlaced> eventType() {
        return OrderPlaced.class;
    }

    @Override
    public Optional<EmailMessage> compose(OrderPlaced event) {
        return Optional.of(new EmailMessage(event.customerEmail(), "Order " + event.orderNumber() + " created",
                "Hello, " + event.customerName() + "! Your order " + event.orderNumber()
                        + " for " + event.total() + " has been created."));
    }
}
