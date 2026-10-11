package com.cycleofsoftwaredev.web.security;

/**
 * Result of a login or a token refresh. The short-lived access token (JWT) is sent with every request,
 * the refresh token is used once to get the next pair without entering the password again.
 */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
