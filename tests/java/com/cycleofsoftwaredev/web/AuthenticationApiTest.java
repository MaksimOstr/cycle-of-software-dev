package com.cycleofsoftwaredev.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.identity.application.UserManagementService;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import com.cycleofsoftwaredev.web.security.AuthRateLimiter;
import com.cycleofsoftwaredev.web.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** JWT authentication: login, access rules by role, refresh, logout and the limit of login requests. */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
@AutoConfigureMockMvc
class AuthenticationApiTest {

    private static final String AUTH = "Authorization";
    private static final String PASSWORD = "Secret12345";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registration;

    @Autowired
    private UserManagementService userManagement;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private StringRedisTemplate redis;

    private UserView customer;
    private UserView administrator;

    @BeforeEach
    void createUsers() {
        String suffix = Long.toString(System.nanoTime());
        customer = registration.register(new RegisterUserCommand("auth" + suffix + "@example.com", "Customer", PASSWORD));
        administrator = registration.createUser(
                new RegisterUserCommand("root" + suffix + "@example.com", "Administrator", PASSWORD), Role.ADMINISTRATOR);
    }

    @Test
    void loginReturnsTokensAndTheAccessTokenIdentifiesTheUser() throws Exception {
        String response = login(customer.email())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(customer.id().toString()))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(response, "$.accessToken");

        assertThat(accessToken.split("\\.")).as("a JWT has a header, a payload and a signature").hasSize(3);
        mvc.perform(get("/api/v1/auth/me").header(AUTH, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.email()));
    }

    @Test
    void requestsWithoutAValidTokenAreRejectedInTheUniformFormat() throws Exception {
        String valid = tokens.issue(customer).accessToken();
        String forged = valid.substring(0, valid.length() - 4) + (valid.endsWith("AAAA") ? "BBBB" : "AAAA");

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/auth/me").header(AUTH, "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(AUTH, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me/orders").header(AUTH, "Bearer " + expiredTokenOf(customer)))
                .andExpect(status().isUnauthorized());
        // the old way of identification does not work any more
        mvc.perform(get("/api/v1/me/orders").header("X-User-Id", customer.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void roleOfTheTokenDecidesWhichEndpointsAreOpen() throws Exception {
        UserView manager = registration.createUser(
                new RegisterUserCommand("mgr" + System.nanoTime() + "@example.com", "Manager", PASSWORD), Role.MANAGER);

        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/management/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/management/orders").header(AUTH, bearer(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/management/orders").header(AUTH, bearer(manager))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header(AUTH, bearer(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header(AUTH, bearer(administrator))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/management/orders").header(AUTH, bearer(administrator))).andExpect(status().isOk());
    }

    @Test
    void refreshTokenWorksOnceAndGivesANewPair() throws Exception {
        String refreshToken = JsonPath.read(login(customer.email()).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        String refreshed = mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(refreshed, "$.accessToken");
        String newRefreshToken = JsonPath.read(refreshed, "$.refreshToken");

        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
        mvc.perform(get("/api/v1/auth/me").header(AUTH, "Bearer " + newAccessToken)).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The refresh token is invalid or has expired"));
        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = JsonPath.read(login(customer.email()).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        mvc.perform(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshedTokenCarriesTheNewRoleAndBlockedUserGetsNoTokens() throws Exception {
        String refreshToken = tokens.issue(customer).refreshToken();
        userManagement.changeRole(customer.id(), Role.MANAGER, administrator.id());

        String refreshed = mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String managerToken = JsonPath.read(refreshed, "$.accessToken");
        mvc.perform(get("/api/v1/management/orders").header(AUTH, "Bearer " + managerToken)).andExpect(status().isOk());

        // a blocked user: the token is still valid, but every request and every refresh is rejected
        userManagement.block(customer.id(), administrator.id());
        mvc.perform(get("/api/v1/management/orders").header(AUTH, "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        String nextRefreshToken = JsonPath.read(refreshed, "$.refreshToken");
        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + nextRefreshToken + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginRequestsOfOneAddressAreLimitedPerMinute() {
        AuthRateLimiter limiter = new AuthRateLimiter(redis, 3, Clock.systemUTC());
        String address = "203.0.113." + UUID.randomUUID();

        assertThat(limiter.tryAcquire(address)).isTrue();
        assertThat(limiter.tryAcquire(address)).isTrue();
        assertThat(limiter.tryAcquire(address)).isTrue();
        assertThat(limiter.tryAcquire(address)).isFalse();
        assertThat(limiter.tryAcquire("198.51.100." + UUID.randomUUID())).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(user).accessToken();
    }

    /** A correctly signed token whose lifetime ended an hour ago. */
    private String expiredTokenOf(UserView user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .claim(TokenService.ROLE_CLAIM, user.role().name())
                .issuedAt(now.minusSeconds(7200))
                .expiresAt(now.minusSeconds(3600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
