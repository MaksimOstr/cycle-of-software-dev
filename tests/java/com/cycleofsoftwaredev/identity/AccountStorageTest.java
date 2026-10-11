package com.cycleofsoftwaredev.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AccountLockedException;
import com.cycleofsoftwaredev.identity.application.AuthenticationException;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
import com.cycleofsoftwaredev.identity.application.PasswordResetService;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.identity.application.UserManagementService;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Identity module with the real PostgreSQL started by Testcontainers. */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class AccountStorageTest {

    private static final String PASSWORD = "Customer12345";

    @Autowired
    private RegistrationService registration;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private PasswordResetService passwordReset;

    @Autowired
    private UserManagementService management;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    /** The database is shared with the other test classes, so every test registers its own users. */
    private String suffix;
    private UserView petro;

    @BeforeEach
    void registerCustomer() {
        suffix = Long.toString(System.nanoTime());
        petro = registration.register(new RegisterUserCommand("Petro" + suffix + "@Example.com", "Petro Customer", PASSWORD));
    }

    /** NFR "Password storage": the table keeps a salted hash, never the password. */
    @Test
    void registeredUserIsStoredWithAHashedPasswordAndFoundByEmail() {
        User stored = users.findByEmail("petro" + suffix + "@example.com").orElseThrow();

        assertThat(stored.id()).isEqualTo(petro.id());
        assertThat(stored.fullName()).isEqualTo("Petro Customer");
        assertThat(stored.role()).isEqualTo(Role.CUSTOMER);
        assertThat(stored.passwordHash()).startsWith("100000:").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM identity.users WHERE id = ?", String.class, petro.id()))
                .isEqualTo(stored.passwordHash());
        assertThat(users.findByEmail("nobody" + suffix + "@example.com")).isEmpty();
    }

    @Test
    void emailBelongsToOneAccount() {
        assertThatThrownBy(() -> registration.register(
                new RegisterUserCommand("PETRO" + suffix + "@example.com", "Another Petro", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
        // two registrations at the same moment both pass the check of the service: the unique index stops the second
        assertThatThrownBy(() -> users.save(new User(UUID.randomUUID(), "petro" + suffix + "@example.com", "Copy",
                "hash", Role.CUSTOMER, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Requirement "Login and logout": a rejected login throws an exception, but the counter of failed attempts
     * must still be saved, otherwise the account would never be locked.
     */
    @Test
    void failedAttemptsAreSavedAlthoughTheLoginIsRejected() {
        for (int attempt = 1; attempt <= User.MAX_FAILED_LOGINS; attempt++) {
            assertThatThrownBy(() -> authentication.authenticate(petro.email(), "wrong password"))
                    .isInstanceOf(AuthenticationException.class);
        }

        assertThat(jdbc.queryForObject("SELECT failed_logins FROM identity.users WHERE id = ?", Integer.class, petro.id()))
                .isEqualTo(User.MAX_FAILED_LOGINS);
        assertThatThrownBy(() -> authentication.authenticate(petro.email(), PASSWORD))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void successfulLoginClearsTheStoredCounter() {
        assertThatThrownBy(() -> authentication.authenticate(petro.email(), "wrong password"))
                .isInstanceOf(AuthenticationException.class);

        assertThat(authentication.authenticate(petro.email().toUpperCase(), PASSWORD)).isEqualTo(petro);

        assertThat(jdbc.queryForObject("SELECT failed_logins FROM identity.users WHERE id = ?", Integer.class, petro.id()))
                .isZero();
    }

    /** Requirement "Password recovery": the link works once and a new request cancels the previous link. */
    @Test
    void passwordIsResetWithTheLatestTokenOnly() {
        passwordReset.requestReset(petro.email());
        String firstToken = storedToken();
        passwordReset.requestReset(petro.email());
        String secondToken = storedToken();

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThatThrownBy(() -> passwordReset.resetPassword(firstToken, "NewPassword1"))
                .isInstanceOf(BusinessRuleException.class);

        passwordReset.resetPassword(secondToken, "NewPassword1");

        assertThat(authentication.authenticate(petro.email(), "NewPassword1").id()).isEqualTo(petro.id());
        assertThatThrownBy(() -> authentication.authenticate(petro.email(), PASSWORD)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> passwordReset.resetPassword(secondToken, "NewPassword2"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.password_reset_tokens WHERE user_id = ?",
                Integer.class, petro.id())).isZero();
    }

    @Test
    void expiredTokenIsRejected() {
        passwordReset.requestReset(petro.email());
        String token = storedToken();
        jdbc.update("UPDATE identity.password_reset_tokens SET expires_at = now() - interval '1 second' WHERE token = ?", token);

        assertThatThrownBy(() -> passwordReset.resetPassword(token, "NewPassword1")).isInstanceOf(BusinessRuleException.class);
        assertThat(authentication.authenticate(petro.email(), PASSWORD).id()).isEqualTo(petro.id());
    }

    /** Requirement "User management": search by email or name, executed and limited by the database. */
    @Test
    void usersAreSearchedByEmailOrNameAndOrderedByEmail() {
        UserView olena = registration.register(
                new RegisterUserCommand("olena" + suffix + "@example.com", "Olena " + suffix + " Shevchenko", PASSWORD));
        UserView andrii = registration.register(
                new RegisterUserCommand("andrii" + suffix + "@example.com", "Andrii 100%_sure", PASSWORD));

        assertThat(management.search(suffix + "@EXAMPLE")).containsExactly(andrii, olena, petro);
        assertThat(management.search("  olena " + suffix + " shev ")).containsExactly(olena);
        assertThat(management.search("nobody" + suffix)).isEmpty();
        // % and _ are ordinary characters, not wildcards
        assertThat(management.search("100%_sure")).contains(andrii);
        assertThat(management.search("%" + suffix)).isEmpty();
        assertThat(users.search(suffix + "@example", 2)).extracting(User::email)
                .containsExactly(andrii.email(), olena.email());
    }

    @Test
    void searchWithoutTextReturnsNoMoreThanTheLimit() {
        registration.register(new RegisterUserCommand("second" + suffix + "@example.com", "Second", PASSWORD));

        List<User> page = users.search("", 2);

        assertThat(page).hasSize(2);
        assertThat(management.search(null).size()).isBetween(2, UserManagementService.MAX_SEARCH_RESULTS);
    }

    @Test
    void blockAndRoleChangeAreStored() {
        UUID administratorId = UUID.randomUUID();

        management.block(petro.id(), administratorId);
        management.changeRole(petro.id(), Role.MANAGER, administratorId);

        assertThat(management.getUser(petro.id()))
                .isEqualTo(new UserView(petro.id(), petro.email(), "Petro Customer", Role.MANAGER, true));
        assertThat(management.unblock(petro.id(), administratorId).blocked()).isFalse();
    }

    private String storedToken() {
        return jdbc.queryForObject("SELECT token FROM identity.password_reset_tokens WHERE user_id = ?", String.class, petro.id());
    }
}
