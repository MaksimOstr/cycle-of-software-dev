package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.identity.api.IdentityApi;
import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.shared.domain.Actor;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Identifies the caller and checks the role. Until JWT authentication is implemented (work item SHOP-17),
 * the user id is passed in the {@value #HEADER} header; only this class has to change when JWT is added.
 */
@Component
public class CurrentUserResolver {

    public static final String HEADER = "X-User-Id";

    private final IdentityApi identity;

    public CurrentUserResolver(IdentityApi identity) {
        this.identity = identity;
    }

    public Actor requireUser(UUID userId) {
        UserView user = findUser(userId);
        return user.role() == Role.CUSTOMER ? Actor.customer(user.id()) : Actor.staff(user.id());
    }

    public Actor requireStaff(UUID userId) {
        Actor actor = requireUser(userId);
        if (!actor.isStaff()) {
            throw new ForbiddenException("This operation is available only to managers and administrators");
        }
        return actor;
    }

    public Actor requireAdministrator(UUID userId) {
        UserView user = findUser(userId);
        if (user.role() != Role.ADMINISTRATOR) {
            throw new ForbiddenException("This operation is available only to administrators");
        }
        return Actor.staff(user.id());
    }

    private UserView findUser(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return identity.findUser(userId).orElseThrow(() -> new UnauthorizedException("Unknown user"));
    }
}
