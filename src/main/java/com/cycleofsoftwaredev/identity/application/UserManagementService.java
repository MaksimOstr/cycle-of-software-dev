package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserAccountChanged;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.shared.events.DomainEventPublisher;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use case "User management": an administrator searches users, blocks and unblocks them and assigns roles. */
@Service
public class UserManagementService {

    private final UserRepository users;
    private final DomainEventPublisher events;
    private final Clock clock;

    public UserManagementService(UserRepository users, DomainEventPublisher events, Clock clock) {
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** Users whose email or name contains the text; all users if the text is empty. */
    public List<UserView> search(String query) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return users.findAll().stream()
                .filter(user -> user.email().contains(text) || user.fullName().toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing(User::email))
                .map(User::toView)
                .toList();
    }

    public UserView getUser(UUID userId) {
        return find(userId).toView();
    }

    public UserView block(UUID userId, UUID administratorId) {
        User user = find(userId);
        requireAnotherUser(user, administratorId, "block");
        user.block();
        return saveAndPublish(user, "blocked", administratorId);
    }

    public UserView unblock(UUID userId, UUID administratorId) {
        User user = find(userId);
        user.unblock();
        return saveAndPublish(user, "unblocked", administratorId);
    }

    public UserView changeRole(UUID userId, Role newRole, UUID administratorId) {
        User user = find(userId);
        requireAnotherUser(user, administratorId, "change the role of");
        user.changeRole(newRole);
        return saveAndPublish(user, "role changed to " + newRole, administratorId);
    }

    /** Protects from a store without administrators: nobody can block or demote their own account. */
    private static void requireAnotherUser(User user, UUID administratorId, String action) {
        if (user.id().equals(administratorId)) {
            throw new BusinessRuleException("An administrator cannot " + action + " their own account");
        }
    }

    private UserView saveAndPublish(User user, String change, UUID administratorId) {
        users.save(user);
        events.publish(new UserAccountChanged(user.id(), user.email(), change, administratorId, clock.instant()));
        return user.toView();
    }

    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
