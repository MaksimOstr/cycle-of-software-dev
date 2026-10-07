package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.AuthenticationService;
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

    public AuthController(RegistrationService registration, AuthenticationService authentication) {
        this.registration = registration;
        this.authentication = authentication;
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank String fullName, @NotBlank String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
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
}
