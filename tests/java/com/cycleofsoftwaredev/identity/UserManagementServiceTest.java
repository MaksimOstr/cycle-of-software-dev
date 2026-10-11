package com.cycleofsoftwaredev.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserAccountChanged;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AccountBlockedException;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.identity.application.UserManagementService;
import com.cycleofsoftwaredev.identity.infrastructure.InMemoryUserRepository;
import com.cycleofsoftwaredev.identity.infrastructure.Pbkdf2PasswordHasher;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.support.MutableClock;
import com.cycleofsoftwaredev.support.RecordingEventPublisher;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserManagementServiceTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T10:00:00Z"));
    private final RegistrationService registration = new RegistrationService(users, hasher, events, clock);
    private final AuthenticationService authentication = new AuthenticationService(users, hasher, clock);
    private final UserManagementService management = new UserManagementService(users, events, clock);

    private final UserView admin = registration.createUser(
            new RegisterUserCommand("admin@example.com", "Store Owner", "Admin12345"), Role.ADMINISTRATOR);
    private final UserView petro = registration.register(
            new RegisterUserCommand("petro@example.com", "Petro Shevchenko", "Petro12345"));
    private final UserView olena = registration.register(
            new RegisterUserCommand("olena@example.com", "Olena Koval", "Olena12345"));

    @Test
    void searchesUsersByEmailOrName() {
        assertThat(management.search(null)).extracting(UserView::email)
                .containsExactly("admin@example.com", "olena@example.com", "petro@example.com");
        assertThat(management.search("KOVAL")).containsExactly(olena);
        assertThat(management.search("petro@")).containsExactly(petro);
        assertThat(management.search("nobody")).isEmpty();
        assertThat(management.getUser(petro.id())).isEqualTo(petro);
    }

    @Test
    void blockedUserCannotLogInUntilUnblocked() {
        assertThat(management.block(petro.id(), admin.id()).blocked()).isTrue();

        assertThatThrownBy(() -> authentication.authenticate("petro@example.com", "Petro12345"))
                .isInstanceOf(AccountBlockedException.class);

        assertThat(management.unblock(petro.id(), admin.id()).blocked()).isFalse();
        assertThat(authentication.authenticate("petro@example.com", "Petro12345")).isEqualTo(petro);
    }

    @Test
    void assignsRoleAndRecordsWhoChangedTheAccount() {
        events.clear();

        UserView manager = management.changeRole(olena.id(), Role.MANAGER, admin.id());

        assertThat(manager.role()).isEqualTo(Role.MANAGER);
        assertThat(events.eventsOfType(UserAccountChanged.class)).singleElement().satisfies(event -> {
            assertThat(event.userId()).isEqualTo(olena.id());
            assertThat(event.change()).isEqualTo("role changed to MANAGER");
            assertThat(event.changedBy()).isEqualTo(admin.id());
        });
    }

    @Test
    void administratorCannotBlockOrDemoteOwnAccount() {
        assertThatThrownBy(() -> management.block(admin.id(), admin.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> management.changeRole(admin.id(), Role.CUSTOMER, admin.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("their own account");
    }

    @Test
    void unknownUserIsReported() {
        assertThatThrownBy(() -> management.block(UUID.randomUUID(), admin.id())).isInstanceOf(NotFoundException.class);
    }
}
