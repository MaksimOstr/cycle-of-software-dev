package com.cycleofsoftwaredev.identity.infrastructure;

import com.cycleofsoftwaredev.identity.domain.PasswordResetToken;
import com.cycleofsoftwaredev.identity.domain.PasswordResetTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of password reset tokens. */
public interface JpaPasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, String>, PasswordResetTokenRepository {
}
