package com.cycleofsoftwaredev.shared.infrastructure;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Delivers domain events to listeners of other modules as Spring application events. */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
