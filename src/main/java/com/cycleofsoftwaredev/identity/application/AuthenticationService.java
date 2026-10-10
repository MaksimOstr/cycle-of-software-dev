package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.domain.PasswordHasher;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Use case "Login". Checks the credentials only; issuing a JWT is the job of the Web and Security layer
 * (work item SHOP-17), so this class does not depend on the token technology.
 */
@Service
public class AuthenticationService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public AuthenticationService(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    public UserView authenticate(String email, String rawPassword) {
        String normalizedEmail;
        try {
            normalizedEmail = User.normalizeEmail(email);
        } catch (IllegalArgumentException invalidEmail) {
            throw new AuthenticationException();
        }
        User user = users.findByEmail(normalizedEmail).orElseThrow(AuthenticationException::new);
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new AuthenticationException();
        }
        return user.toView();
    }
}
