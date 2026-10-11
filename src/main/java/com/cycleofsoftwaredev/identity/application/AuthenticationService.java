package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.domain.PasswordHasher;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case "Login". Checks the credentials only; issuing a JWT is the job of the Web and Security layer
 * (work item SHOP-17), so this class does not depend on the token technology. A rejected login does not
 * roll the transaction back: the counter of failed attempts has to be saved.
 */
@Service
@Transactional(noRollbackFor = {AuthenticationException.class, AccountLockedException.class, AccountBlockedException.class})
public class AuthenticationService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AuthenticationService(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    public UserView authenticate(String email, String rawPassword) {
        String normalizedEmail;
        try {
            normalizedEmail = User.normalizeEmail(email);
        } catch (IllegalArgumentException invalidEmail) {
            throw new AuthenticationException();
        }
        User user = users.findByEmail(normalizedEmail).orElseThrow(AuthenticationException::new);
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            throw new AccountLockedException();
        }
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            user.loginFailed(now);
            users.save(user);
            throw new AuthenticationException();
        }
        user.loginSucceeded();
        users.save(user);
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }
        return user.toView();
    }
}
