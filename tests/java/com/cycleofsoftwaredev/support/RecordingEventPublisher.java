package com.cycleofsoftwaredev.support;

import com.cycleofsoftwaredev.shared.events.DomainEvent;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Test double of the event bus: remembers published events and can forward them to listeners. */
public class RecordingEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> events = new ArrayList<>();
    private final List<Consumer<DomainEvent>> listeners = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        events.add(event);
        List.copyOf(listeners).forEach(listener -> listener.accept(event));
    }

    public void subscribe(Consumer<DomainEvent> listener) {
        listeners.add(listener);
    }

    public <T extends DomainEvent> List<T> eventsOfType(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public void clear() {
        events.clear();
    }
}
