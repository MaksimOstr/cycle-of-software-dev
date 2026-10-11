package com.cycleofsoftwaredev.web.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the tokens: {@code shopsphere.jwt.*}. */
@ConfigurationProperties("shopsphere.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
