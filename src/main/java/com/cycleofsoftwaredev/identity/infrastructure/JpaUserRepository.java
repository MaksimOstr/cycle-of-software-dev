package com.cycleofsoftwaredev.identity.infrastructure;

import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of users; the methods of {@link UserRepository} are implemented by Spring Data. */
public interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {
}
