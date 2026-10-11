package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
import com.cycleofsoftwaredev.identity.application.PasswordResetService;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
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

    public AuthController(RegistrationService registration, AuthenticationService authentication,
                          PasswordResetService passwordReset) {
        this.registration = registration;
        this.authentication = authentication;
        this.passwordReset = passwordReset;
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank String fullName, @NotBlank String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record NewPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return registration.register(new RegisterUserCommand(request.email(), request.fullName(), request.password()));
    }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request) {
        return authentication.authenticate(request.email(), request.password());
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
