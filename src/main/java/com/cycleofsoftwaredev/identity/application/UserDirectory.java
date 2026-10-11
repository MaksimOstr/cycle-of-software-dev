package com.cycleofsoftwaredev.identity.application;

import com.cycleofsoftwaredev.identity.api.IdentityApi;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to users for other modules. */
@Service
@Transactional(readOnly = true)
public class UserDirectory implements IdentityApi {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserView> findUser(UUID userId) {
        return users.findById(userId).map(User::toView);
    }

    @Override
    public Optional<UserView> findUserByEmail(String email) {
        return users.findByEmail(User.normalizeEmail(email)).map(User::toView);
    }
}
