package com.cycleofsoftwaredev.ordering.api;

/** States of the order lifecycle (Laboratory Work 2, state machine diagram of an order). */
public enum OrderStatus {
    NEW,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED
}
