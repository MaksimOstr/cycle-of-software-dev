package com.cycleofsoftwaredev.identity.application;

/** Wrong email or password. The message intentionally does not say which of them is wrong. */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException() {
        super("Invalid email or password");
    }
}
