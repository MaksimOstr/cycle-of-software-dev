package com.cycleofsoftwaredev.shared.domain;

/** The requested entity does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
