package com.cycleofsoftwaredev.ordering.domain;

import java.util.regex.Pattern;

/** Contact data of the person who placed the order (a guest or a registered customer). */
public record ContactInfo(String fullName, String email, String phone) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{10,20}$");

    public ContactInfo {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("Invalid phone number: " + phone);
        }
        fullName = fullName.trim();
    }
}
