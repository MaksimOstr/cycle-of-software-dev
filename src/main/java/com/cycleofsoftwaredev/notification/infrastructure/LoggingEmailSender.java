package com.cycleofsoftwaredev.notification.infrastructure;

import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import com.cycleofsoftwaredev.notification.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes emails to the log instead of sending them; replaced by the SMTP adapter in production. */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("EMAIL to={} subject='{}' body='{}'", message.to(), message.subject(), message.body());
    }
}
