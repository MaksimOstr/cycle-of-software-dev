package com.cycleofsoftwaredev.notification.application;

import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.notification.domain.EmailComposer;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WelcomeEmailComposer implements EmailComposer<UserRegistered> {

    @Override
    public Class<UserRegistered> eventType() {
        return UserRegistered.class;
    }

    @Override
    public Optional<EmailMessage> compose(UserRegistered event) {
        return Optional.of(new EmailMessage(event.email(), "Welcome to ShopSphere",
                "Hello, " + event.fullName() + "! Your ShopSphere account has been created."));
    }
}
