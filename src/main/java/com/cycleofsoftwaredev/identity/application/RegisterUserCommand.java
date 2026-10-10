package com.cycleofsoftwaredev.identity.application;

public record RegisterUserCommand(String email, String fullName, String password) {
}
