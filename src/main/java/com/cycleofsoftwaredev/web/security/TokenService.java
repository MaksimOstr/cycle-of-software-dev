package com.cycleofsoftwaredev.web.security;

import com.cycleofsoftwaredev.identity.api.IdentityApi;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.web.ForbiddenException;
import com.cycleofsoftwaredev.web.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues tokens after a successful login. The access token is a signed JWT with the user id (subject),
 * the role and the email; the server keeps no session. The refresh token is a random string kept in Redis.
 */
@Service
public class TokenService {

    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";
    private static final String ISSUER = "shopsphere";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder encoder;
    private final RefreshTokenStore refreshTokens;
    private final IdentityApi identity;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, RefreshTokenStore refreshTokens, IdentityApi identity,
                        JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.identity = identity;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenPair issue(UserView user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(user.id().toString())
                .claim(ROLE_CLAIM, user.role().name())
                .claim(EMAIL_CLAIM, user.email())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .build();
        String accessToken = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String refreshToken = newRefreshToken();
        refreshTokens.save(refreshToken, user.id(), properties.refreshTokenTtl());
        return new TokenPair(accessToken, refreshToken, properties.accessTokenTtl().toSeconds());
    }

    /**
     * Exchanges a refresh token for a new pair. The old refresh token stops working (rotation), and the new
     * access token gets the current role of the user, so a role change takes effect without a new login.
     */
    public TokenPair refresh(String refreshToken) {
        UUID userId = refreshTokens.consume(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("The refresh token is invalid or has expired"));
        UserView user = identity.findUser(userId)
                .orElseThrow(() -> new UnauthorizedException("The refresh token is invalid or has expired"));
        if (user.blocked()) {
            throw new ForbiddenException("The account is blocked");
        }
        return issue(user);
    }

    /** Logout: the refresh token is removed, so no new access tokens can be obtained with it. */
    public void revoke(String refreshToken) {
        refreshTokens.consume(refreshToken);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
