package com.cycleofsoftwaredev.identity.domain;

import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;

/** Password rules from the "User registration" requirement: at least 8 characters with letters and digits. */
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new BusinessRuleException("Password must contain at least " + MIN_LENGTH + " characters");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessRuleException("Password must contain both letters and digits");
        }
    }
}
