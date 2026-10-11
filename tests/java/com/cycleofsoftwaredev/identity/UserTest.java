package com.cycleofsoftwaredev.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.domain.PasswordResetToken;
import com.cycleofsoftwaredev.identity.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules of a user account, without the services and the storage. */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final User user = new User(UUID.randomUUID(), "  Petro@Example.COM ", " Petro Customer ", "hash",
            Role.CUSTOMER, NOW);

    @Test
    void emailIsStoredInLowerCaseAndTheNameWithoutSpacesAround() {
        assertThat(user.email()).isEqualTo("petro@example.com");
        assertThat(user.fullName()).isEqualTo("Petro Customer");
        assertThat(user.registeredAt()).isEqualTo(NOW);
        assertThat(user.toView()).isEqualTo(new UserView(user.id(), "petro@example.com", "Petro Customer", Role.CUSTOMER, false));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "petro", "petro@", "@example.com", "petro@example", "petro @example.com", "a@b@c.com"})
    void invalidEmailIsRejected(String email) {
        assertThatThrownBy(() -> User.normalizeEmail(email)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameRoleAndPasswordHashAreRequired() {
        assertThatThrownBy(() -> user.rename(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.rename(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changeRole(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> user.changePassword(null)).isInstanceOf(NullPointerException.class);
        assertThat(user.fullName()).isEqualTo("Petro Customer");
        assertThat(user.role()).isEqualTo(Role.CUSTOMER);
    }

    /** Requirement "Login and logout": 5 consecutive failed attempts lock the account for 15 minutes. */
    @Test
    void fifthFailedLoginLocksTheAccountForFifteenMinutes() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            user.loginFailed(NOW);
        }
        assertThat(user.isLocked(NOW)).isFalse();

        user.loginFailed(NOW);

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void attemptsAreCountedFromZeroAfterTheLockHasExpired() {
        for (int attempt = 1; attempt <= User.MAX_FAILED_LOGINS; attempt++) {
            user.loginFailed(NOW);
        }
        Instant later = NOW.plus(User.LOCK_DURATION);

        user.loginFailed(later);

        assertThat(user.isLocked(later)).isFalse();
        for (int attempt = 2; attempt <= User.MAX_FAILED_LOGINS; attempt++) {
            user.loginFailed(later);
        }
        assertThat(user.isLocked(later)).isTrue();
    }

    @Test
    void successfulLoginForgetsTheFailedAttempts() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            user.loginFailed(NOW);
        }

        user.loginSucceeded();
        user.loginFailed(NOW);

        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    void newPasswordRemovesTheLock() {
        for (int attempt = 1; attempt <= User.MAX_FAILED_LOGINS; attempt++) {
            user.loginFailed(NOW);
        }

        user.changePassword("new hash");

        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.passwordHash()).isEqualTo("new hash");
    }

    @Test
    void administratorBlocksUnblocksAndPromotesTheUser() {
        user.block();
        assertThat(user.isBlocked()).isTrue();
        assertThat(user.toView().blocked()).isTrue();

        user.unblock();
        user.changeRole(Role.MANAGER);

        assertThat(user.isBlocked()).isFalse();
        assertThat(user.toView().role()).isEqualTo(Role.MANAGER);
    }

    /** Requirement "Password recovery": the link from the email is valid for 1 hour. */
    @Test
    void passwordResetTokenExpiresExactlyInOneHour() {
        PasswordResetToken token = PasswordResetToken.issue("token", user.id(), NOW);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(token.isExpired(NOW.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(token.isExpired(NOW.plus(Duration.ofHours(1)))).isTrue();
        assertThat(token.value()).isEqualTo("token");
        assertThat(token.userId()).isEqualTo(user.id());
    }
}
