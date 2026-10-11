package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.api.PasswordResetRequested;
import com.cycleofsoftwaredev.identity.domain.PasswordHasher;
import com.cycleofsoftwaredev.identity.domain.PasswordPolicy;
import com.cycleofsoftwaredev.identity.domain.PasswordResetToken;
import com.cycleofsoftwaredev.identity.domain.PasswordResetTokenRepository;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use case "Password recovery": a reset token is sent by email, is valid for 1 hour and works only once. */
@Service
@Transactional
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordHasher passwordHasher;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordHasher passwordHasher,
                                DomainEventPublisher events, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Sends the reset token to the email. Nothing happens for an unknown email, and the caller gets the same
     * answer in both cases, so the API cannot be used to find out which emails are registered.
     */
    public void requestReset(String email) {
        findByEmail(email).ifPresent(user -> {
            Instant now = clock.instant();
            tokens.deleteByUserId(user.id());
            PasswordResetToken token = tokens.save(PasswordResetToken.issue(newTokenValue(), user.id(), now));
            events.publish(new PasswordResetRequested(user.id(), user.email(), token.value(), token.expiresAt(), now));
        });
    }

    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = tokens.findByToken(tokenValue == null ? "" : tokenValue)
                .filter(found -> !found.isExpired(clock.instant()))
                .orElseThrow(() -> new BusinessRuleException("The password reset link is invalid or has expired"));
        passwordPolicy.validate(newPassword);

        User user = users.findById(token.userId()).orElseThrow();
        user.changePassword(passwordHasher.hash(newPassword));
        users.save(user);
        tokens.deleteByUserId(user.id());
    }

    private Optional<User> findByEmail(String email) {
        try {
            return users.findByEmail(User.normalizeEmail(email));
        } catch (IllegalArgumentException invalidEmail) {
            return Optional.empty();
        }
    }

    private String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
