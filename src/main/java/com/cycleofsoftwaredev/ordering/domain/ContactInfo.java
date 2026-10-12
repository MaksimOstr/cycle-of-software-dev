package com.cycleofsoftwaredev.ordering.domain;

import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;

/** Contact data of the person who placed the order (a guest or a registered customer). */
@Embeddable
public record ContactInfo(String fullName, String email, String phone) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{10,20}$");
    /** A number with the operator code, like 050 123 45 67; spaces, brackets and hyphens are not counted. */
    private static final int MIN_PHONE_DIGITS = 10;

    public ContactInfo {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
        if (phone == null || !PHONE.matcher(phone).matches()
                || phone.chars().filter(Character::isDigit).count() < MIN_PHONE_DIGITS) {
            throw new IllegalArgumentException("Invalid phone number: " + phone);
        }
        fullName = fullName.trim();
    }
}
