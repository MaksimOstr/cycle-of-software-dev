package com.cycleofsoftwaredev.identity.application;

/** The credentials are correct, but the account was blocked by an administrator. */
public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException() {
        super("The account is blocked. Contact the store administrator");
    }
}
