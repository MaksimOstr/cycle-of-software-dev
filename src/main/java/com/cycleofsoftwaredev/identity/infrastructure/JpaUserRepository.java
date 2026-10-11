package com.cycleofsoftwaredev.identity.infrastructure;

import com.cycleofsoftwaredev.identity.domain.User;
import com.cycleofsoftwaredev.identity.domain.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PostgreSQL storage of users; the methods of {@link UserRepository} are implemented by Spring Data. */
public interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {

    /** strpos looks for the text as it is, so characters like % and _ have no special meaning. */
    @Override
    @Query(value = """
            SELECT * FROM identity.users u
            WHERE :text = '' OR strpos(u.email, :text) > 0 OR strpos(lower(u.full_name), :text) > 0
            ORDER BY u.email
            LIMIT :limit""", nativeQuery = true)
    List<User> search(@Param("text") String text, @Param("limit") int limit);
}
