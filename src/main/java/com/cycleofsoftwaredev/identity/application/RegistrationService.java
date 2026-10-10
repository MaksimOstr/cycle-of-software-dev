package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.domain.PasswordHasher;
import com.cycleofsoftwaredev.identity.domain.PasswordPolicy;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use case "User registration". */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    public RegistrationService(UserRepository users, PasswordHasher passwordHasher, DomainEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    public UserView register(RegisterUserCommand command) {
        return createUser(command, Role.CUSTOMER);
    }

    /** Creates an account with the given role; administrators use it to add managers. */
    public UserView createUser(RegisterUserCommand command, Role role) {
        String email = User.normalizeEmail(command.email());
        if (users.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("A user with email " + email + " already exists");
        }
        passwordPolicy.validate(command.password());

        User user = new User(UUID.randomUUID(), email, command.fullName(),
                passwordHasher.hash(command.password()), role, clock.instant());
        users.save(user);
        events.publish(new UserRegistered(user.id(), user.email(), user.fullName(), clock.instant()));
        return user.toView();
    }
}
