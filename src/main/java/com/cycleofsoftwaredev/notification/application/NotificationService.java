package com.cycleofsoftwaredev.notification.application;

import com.cycleofsoftwaredev.notification.domain.EmailComposer;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import com.cycleofsoftwaredev.notification.domain.EmailSender;
import com.cycleofsoftwaredev.shared.events.DomainEvent;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Sends emails in reaction to domain events of other modules. A failed email must not break the business
 * operation, so errors are logged (with the transactional outbox they will be retried).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<EmailComposer<?>> composers;
    private final EmailSender emailSender;

    public NotificationService(List<EmailComposer<?>> composers, EmailSender emailSender) {
        this.composers = composers;
        this.emailSender = emailSender;
    }

    @EventListener
    public void onEvent(DomainEvent event) {
        for (EmailComposer<?> composer : composers) {
            compose(composer, event).ifPresent(this::sendSafely);
        }
    }

    private static <E extends DomainEvent> Optional<EmailMessage> compose(EmailComposer<E> composer, DomainEvent event) {
        if (!composer.eventType().isInstance(event)) {
            return Optional.empty();
        }
        return composer.compose(composer.eventType().cast(event));
    }

    private void sendSafely(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (RuntimeException e) {
            log.error("Failed to send email '{}' to {}", message.subject(), message.to(), e);
        }
    }
}
