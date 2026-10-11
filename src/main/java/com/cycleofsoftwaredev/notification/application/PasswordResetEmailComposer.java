package com.cycleofsoftwaredev.notification.application;

import com.cycleofsoftwaredev.identity.api.PasswordResetRequested;
import com.cycleofsoftwaredev.notification.domain.EmailComposer;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailComposer implements EmailComposer<PasswordResetRequested> {

    @Override
    public Class<PasswordResetRequested> eventType() {
        return PasswordResetRequested.class;
    }

    @Override
    public Optional<EmailMessage> compose(PasswordResetRequested event) {
        return Optional.of(new EmailMessage(event.email(), "ShopSphere password reset",
                "To set a new password use this code: " + event.token()
                        + " . It is valid for 1 hour and can be used only once. "
                        + "If you did not ask for a password reset, ignore this email."));
    }
}
