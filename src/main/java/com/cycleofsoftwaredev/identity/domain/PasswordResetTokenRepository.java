package com.cycleofsoftwaredev.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByToken(String token);

    /** Removes all tokens of the user: a token can be used only once and a new request cancels the old link. */
    void deleteByUserId(UUID userId);
}
