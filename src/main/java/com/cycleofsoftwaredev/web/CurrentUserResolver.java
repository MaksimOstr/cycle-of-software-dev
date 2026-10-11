package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.identity.api.IdentityApi;
import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.shared.domain.Actor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Identifies the caller by the access token that Spring Security has already validated and checks the role.
 * The user is read from the database on every request, so a blocked account and a changed role take effect
 * at once, without waiting for the token to expire.
 */
@Component
public class CurrentUserResolver {

    private final IdentityApi identity;

    public CurrentUserResolver(IdentityApi identity) {
        this.identity = identity;
    }

    /** The signed-in user, or empty for a guest (a request without a token). */
    public Optional<Actor> optionalUser() {
        return authenticatedUserId().map(userId -> toActor(findUser(userId)));
    }

    public Actor requireUser() {
        return toActor(currentUser());
    }

    public Actor requireStaff() {
        Actor actor = requireUser();
        if (!actor.isStaff()) {
            throw new ForbiddenException("This operation is available only to managers and administrators");
        }
        return actor;
    }

    public Actor requireAdministrator() {
        UserView user = currentUser();
        if (user.role() != Role.ADMINISTRATOR) {
            throw new ForbiddenException("This operation is available only to administrators");
        }
        return Actor.staff(user.id());
    }

    /** Data of the signed-in user for {@code GET /api/v1/auth/me}. */
    public UserView currentUser() {
        UUID userId = authenticatedUserId().orElseThrow(() -> new UnauthorizedException("Authentication is required"));
        return findUser(userId);
    }

    private static Actor toActor(UserView user) {
        return user.role() == Role.CUSTOMER ? Actor.customer(user.id()) : Actor.staff(user.id());
    }

    private static Optional<UUID> authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(UUID.fromString(token.getToken().getSubject()));
        }
        return Optional.empty();
    }

    private UserView findUser(UUID userId) {
        UserView user = identity.findUser(userId).orElseThrow(() -> new UnauthorizedException("Unknown user"));
        if (user.blocked()) {
            throw new ForbiddenException("The account is blocked");
        }
        return user;
    }
}
