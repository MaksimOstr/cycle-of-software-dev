package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.domain.User;

/** Too many failed login attempts: the account is temporarily locked. */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("The account is locked for " + User.LOCK_DURATION.toMinutes() + " minutes after "
                + User.MAX_FAILED_LOGINS + " failed login attempts");
    }
}
