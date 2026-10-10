package com.cycleofsoftwaredev.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AuthenticationException;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.identity.application.UserDirectory;
import com.cycleofsoftwaredev.identity.infrastructure.InMemoryUserRepository;
import com.cycleofsoftwaredev.identity.infrastructure.Pbkdf2PasswordHasher;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.support.MutableClock;
import com.cycleofsoftwaredev.support.RecordingEventPublisher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegistrationServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final RegistrationService registration =
            new RegistrationService(users, hasher, events, new MutableClock(Instant.parse("2026-10-05T10:00:00Z")));
    private final AuthenticationService authentication = new AuthenticationService(users, hasher);

    @Test
    void registersCustomerWithHashedPassword() {
        UserView user = registration.register(new RegisterUserCommand(" Ivan@Example.com ", "Ivan Petrenko", "secret123"));

        assertThat(user.email()).isEqualTo("ivan@example.com");
        assertThat(user.role()).isEqualTo(Role.CUSTOMER);
        assertThat(users.findById(user.id()).orElseThrow().passwordHash()).doesNotContain("secret123");
        assertThat(events.eventsOfType(UserRegistered.class)).singleElement()
                .extracting(UserRegistered::userId).isEqualTo(user.id());
        assertThat(new UserDirectory(users).findUser(user.id())).contains(user);
    }

    @Test
    void rejectsDuplicateEmail() {
        registration.register(new RegisterUserCommand("ivan@example.com", "Ivan", "secret123"));

        assertThatThrownBy(() -> registration.register(new RegisterUserCommand("IVAN@example.com", "Other", "secret456")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsWeakPasswords() {
        assertThatThrownBy(() -> registration.register(new RegisterUserCommand("a@example.com", "A", "short1")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> registration.register(new RegisterUserCommand("a@example.com", "A", "onlyletters")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void authenticatesWithCorrectPasswordOnly() {
        UserView user = registration.register(new RegisterUserCommand("ivan@example.com", "Ivan", "secret123"));

        assertThat(authentication.authenticate("IVAN@example.com", "secret123")).isEqualTo(user);
        assertThatThrownBy(() -> authentication.authenticate("ivan@example.com", "wrong123"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> authentication.authenticate("nobody@example.com", "secret123"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> authentication.authenticate("not-an-email", "secret123"))
                .isInstanceOf(AuthenticationException.class);
    }
}
