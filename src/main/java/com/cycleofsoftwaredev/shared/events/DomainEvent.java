package com.cycleofsoftwaredev.shared.events;

import java.time.Instant;

/** Something that happened in a module and may be interesting for other modules (notifications, audit log). */
public interface DomainEvent {

    Instant occurredAt();
}
