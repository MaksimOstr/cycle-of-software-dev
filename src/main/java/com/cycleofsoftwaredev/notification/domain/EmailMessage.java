package com.cycleofsoftwaredev.notification.domain;

import java.util.Objects;

public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(body);
    }
}
