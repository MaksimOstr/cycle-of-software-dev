package com.cycleofsoftwaredev.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.identity.domain.PasswordPolicy;
import com.cycleofsoftwaredev.identity.infrastructure.Pbkdf2PasswordHasher;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Requirement "User registration" (password rules) and NFR "Password storage". */
class PasswordRulesTest {

    private final PasswordPolicy policy = new PasswordPolicy();
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @ParameterizedTest
    @ValueSource(strings = {"Passw0rd", "12345678a", "a1a1a1a1", "пароль2026", "Very long passphrase with 1 digit"})
    void passwordOfEightCharactersWithALetterAndADigitIsAccepted(String password) {
        assertThatCode(() -> policy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Pass1", "Passw0r", "password", "PASSWORD", "12345678", "!!!!!!!!1", "!!!!!!!!a"})
    void shortPasswordOrPasswordWithoutLettersOrDigitsIsRejected(String password) {
        assertThatThrownBy(() -> policy.validate(password)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void hashContainsNeitherThePasswordNorTheSameValueTwice() {
        String first = hasher.hash("Customer12345");
        String second = hasher.hash("Customer12345");

        assertThat(first).doesNotContain("Customer12345").isNotEqualTo(second); // every password has its own salt
        assertThat(first).matches("100000:[A-Za-z0-9+/=]{24}:[A-Za-z0-9+/=]{44}");
        assertThat(first.length()).isLessThanOrEqualTo(255); // size of the password_hash column
    }

    @Test
    void onlyTheRightPasswordMatchesTheHash() {
        String hash = hasher.hash("Customer12345");

        assertThat(hasher.matches("Customer12345", hash)).isTrue();
        assertThat(hasher.matches("customer12345", hash)).isFalse();
        assertThat(hasher.matches("Customer12345 ", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
    }

    /** A damaged value in the database must look like a wrong password, not like a server error. */
    @ParameterizedTest
    @ValueSource(strings = {"", "plain-text-password", "100000:salt", "abc:def:ghi", "100000:!!!:???", "0:AAAA:AAAA",
            "-5:AAAA:AAAA"})
    void damagedHashMatchesNoPassword(String storedHash) {
        assertThat(hasher.matches("Customer12345", storedHash)).isFalse();
    }
}
