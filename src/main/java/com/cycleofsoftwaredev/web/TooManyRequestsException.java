package com.cycleofsoftwaredev.web;

/** The client sends requests too often. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
