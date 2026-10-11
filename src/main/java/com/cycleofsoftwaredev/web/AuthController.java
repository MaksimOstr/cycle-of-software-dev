package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
import com.cycleofsoftwaredev.identity.application.PasswordResetService;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.web.security.TokenPair;
import com.cycleofsoftwaredev.web.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final PasswordResetService passwordReset;
    private final TokenService tokens;
    private final CurrentUserResolver currentUser;

    public AuthController(RegistrationService registration, AuthenticationService authentication,
                          PasswordResetService passwordReset, TokenService tokens, CurrentUserResolver currentUser) {
        this.registration = registration;
        this.authentication = authentication;
        this.passwordReset = passwordReset;
        this.tokens = tokens;
        this.currentUser = currentUser;
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank String fullName, @NotBlank String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record NewPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    /** Tokens for the "Authorization: Bearer" header together with the data of the signed-in user. */
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user) {

        static TokenResponse of(TokenPair pair, UserView user) {
            return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds(), user);
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return registration.register(new RegisterUserCommand(request.email(), request.fullName(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        UserView user = authentication.authenticate(request.email(), request.password());
        return TokenResponse.of(tokens.issue(user), user);
    }

    /** A new pair of tokens for a valid refresh token; the used refresh token stops working. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenPair pair = tokens.refresh(request.refreshToken());
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds(), null);
    }

    /** Requirement "Login and logout": the refresh token is revoked, the access token expires in minutes. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokens.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    public UserView me() {
        return currentUser.currentUser();
    }

    /** The answer is the same for registered and unknown emails. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordReset.requestReset(request.email());
        return new MessageResponse("If the email is registered, a password reset code has been sent to it");
    }

    @PostMapping("/password-reset/confirm")
    public MessageResponse resetPassword(@Valid @RequestBody NewPasswordRequest request) {
        passwordReset.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("The password has been changed");
    }
}
