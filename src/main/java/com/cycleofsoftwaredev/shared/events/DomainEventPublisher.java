package com.cycleofsoftwaredev.shared.events;

/**
 * Abstraction of the Domain Event Bus. Modules publish events through this interface and never depend on the
 * concrete messaging technology (Spring application events now, a transactional outbox later).
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
