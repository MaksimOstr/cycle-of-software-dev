package com.cycleofsoftwaredev.shared.domain;

/** A business rule was violated (for example, an order cannot be cancelled after shipment). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
