package com.cycleofsoftwaredev.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.identity.api.PasswordResetRequested;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AccountLockedException;
import com.cycleofsoftwaredev.identity.application.AuthenticationException;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
import com.cycleofsoftwaredev.identity.application.PasswordResetService;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.identity.infrastructure.InMemoryPasswordResetTokenRepository;
import com.cycleofsoftwaredev.identity.infrastructure.InMemoryUserRepository;
import com.cycleofsoftwaredev.identity.infrastructure.Pbkdf2PasswordHasher;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.support.MutableClock;
import com.cycleofsoftwaredev.support.RecordingEventPublisher;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Requirements "Login and logout" (lock after failed attempts) and "Password recovery". */
class AccountSecurityTest {

    private static final String EMAIL = "ivan@example.com";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T10:00:00Z"));
    private final RegistrationService registration = new RegistrationService(users, hasher, events, clock);
    private final AuthenticationService authentication = new AuthenticationService(users, hasher, clock);
    private final PasswordResetService passwordReset =
            new PasswordResetService(users, new InMemoryPasswordResetTokenRepository(), hasher, events, clock);

    private UserView user;

    @BeforeEach
    void registerUser() {
        user = registration.register(new RegisterUserCommand(EMAIL, "Ivan", "secret123"));
    }

    @Test
    void locksAccountAfterFiveFailedLoginsForFifteenMinutes() {
        failLogin(5);

        assertThatThrownBy(() -> authentication.authenticate(EMAIL, "secret123"))
                .isInstanceOf(AccountLockedException.class);

        clock.advance(Duration.ofMinutes(15));
        assertThat(authentication.authenticate(EMAIL, "secret123")).isEqualTo(user);
    }

    @Test
    void successfulLoginResetsTheCounterOfFailedAttempts() {
        failLogin(4);
        authentication.authenticate(EMAIL, "secret123");
        failLogin(4);

        assertThat(authentication.authenticate(EMAIL, "secret123")).isEqualTo(user);
    }

    @Test
    void resetsPasswordWithTokenFromEmail() {
        passwordReset.requestReset(" IVAN@example.com ");
        PasswordResetRequested requested = events.eventsOfType(PasswordResetRequested.class).getFirst();

        passwordReset.resetPassword(requested.token(), "newSecret456");

        assertThat(requested.email()).isEqualTo(EMAIL);
        assertThat(requested.toString()).doesNotContain(requested.token());
        assertThat(authentication.authenticate(EMAIL, "newSecret456")).isEqualTo(user);
        assertThatThrownBy(() -> authentication.authenticate(EMAIL, "secret123"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void tokenWorksOnlyOnce() {
        String token = requestToken();
        passwordReset.resetPassword(token, "newSecret456");

        assertThatThrownBy(() -> passwordReset.resetPassword(token, "another789"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void tokenExpiresInOneHour() {
        String token = requestToken();
        clock.advance(Duration.ofHours(1));

        assertThatThrownBy(() -> passwordReset.resetPassword(token, "newSecret456"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void newRequestCancelsThePreviousToken() {
        String first = requestToken();
        String second = requestToken();

        assertThatThrownBy(() -> passwordReset.resetPassword(first, "newSecret456"))
                .isInstanceOf(BusinessRuleException.class);
        passwordReset.resetPassword(second, "newSecret456");
    }

    @Test
    void newPasswordMustFollowThePasswordPolicyAndTokenStaysValid() {
        String token = requestToken();

        assertThatThrownBy(() -> passwordReset.resetPassword(token, "short"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 8 characters");
        passwordReset.resetPassword(token, "newSecret456");
    }

    @Test
    void unknownEmailAndUnknownTokenAreIgnoredOrRejected() {
        passwordReset.requestReset("nobody@example.com");
        passwordReset.requestReset("not-an-email");

        assertThat(events.eventsOfType(PasswordResetRequested.class)).isEmpty();
        assertThatThrownBy(() -> passwordReset.resetPassword("unknown", "newSecret456"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> passwordReset.resetPassword(null, "newSecret456"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void passwordResetUnlocksTheAccount() {
        failLogin(5);

        passwordReset.resetPassword(requestToken(), "newSecret456");

        assertThat(authentication.authenticate(EMAIL, "newSecret456")).isEqualTo(user);
    }

    private String requestToken() {
        events.clear();
        passwordReset.requestReset(EMAIL);
        return events.eventsOfType(PasswordResetRequested.class).getFirst().token();
    }

    private void failLogin(int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> authentication.authenticate(EMAIL, "wrong-password"))
                    .isInstanceOf(AuthenticationException.class);
        }
    }
}
