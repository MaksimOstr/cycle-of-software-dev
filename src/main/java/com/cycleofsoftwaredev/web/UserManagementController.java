package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.UserManagementService;
import com.cycleofsoftwaredev.shared.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator endpoints of the requirement "User management". */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController {

    private final UserManagementService users;
    private final CurrentUserResolver currentUser;

    public UserManagementController(UserManagementService users, CurrentUserResolver currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    public record RoleRequest(@NotNull Role role) {
    }

    @GetMapping
    public List<UserView> search(@RequestParam(required = false) String query) {
        currentUser.requireAdministrator();
        return users.search(query);
    }

    @GetMapping("/{id}")
    public UserView details(@PathVariable UUID id) {
        currentUser.requireAdministrator();
        return users.getUser(id);
    }

    @PostMapping("/{id}/block")
    public UserView block(@PathVariable UUID id) {
        Actor administrator = currentUser.requireAdministrator();
        return users.block(id, administrator.userId());
    }

    @PostMapping("/{id}/unblock")
    public UserView unblock(@PathVariable UUID id) {
        Actor administrator = currentUser.requireAdministrator();
        return users.unblock(id, administrator.userId());
    }

    @PutMapping("/{id}/role")
    public UserView changeRole(@PathVariable UUID id,
                               @Valid @RequestBody RoleRequest request) {
        Actor administrator = currentUser.requireAdministrator();
        return users.changeRole(id, request.role(), administrator.userId());
    }
}
