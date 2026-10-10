package com.cycleofsoftwaredev.notification.domain;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.util.Optional;

/**
 * Builds the email for one type of domain event. A new kind of notification is added as a new composer,
 * without changing the notification service (Open/Closed principle).
 */
public interface EmailComposer<E extends DomainEvent> {

    Class<E> eventType();

    /** Returns an empty result if this event does not require an email. */
    Optional<EmailMessage> compose(E event);
}
