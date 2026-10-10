package com.cycleofsoftwaredev.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.notification.application.NotificationService;
import com.cycleofsoftwaredev.notification.application.OrderCreatedEmailComposer;
import com.cycleofsoftwaredev.notification.application.OrderStatusEmailComposer;
import com.cycleofsoftwaredev.notification.application.WelcomeEmailComposer;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import com.cycleofsoftwaredev.ordering.api.OrderPlaced;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.OrderStatusChanged;
import com.cycleofsoftwaredev.payment.api.PaymentSucceeded;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final List<EmailMessage> sent = new ArrayList<>();
    private final NotificationService notifications = new NotificationService(
            List.of(new WelcomeEmailComposer(), new OrderCreatedEmailComposer(), new OrderStatusEmailComposer()),
            sent::add);

    @Test
    void sendsOrderConfirmationAndStatusEmails() {
        UUID orderId = UUID.randomUUID();
        notifications.onEvent(new OrderPlaced(orderId, "SS-1", "petro@example.com", "Petro", Money.of("1000"), NOW));
        notifications.onEvent(new OrderStatusChanged(orderId, "SS-1", "petro@example.com", OrderStatus.PROCESSING,
                OrderStatus.SHIPPED, "20450000000001", NOW));

        assertThat(sent).extracting(EmailMessage::to).containsOnly("petro@example.com");
        assertThat(sent).extracting(EmailMessage::subject).containsExactly("Order SS-1 created", "Order SS-1: shipped");
        assertThat(sent.get(1).body()).contains("20450000000001");
    }

    @Test
    void sendsWelcomeEmailAfterRegistration() {
        notifications.onEvent(new UserRegistered(UUID.randomUUID(), "ivan@example.com", "Ivan", NOW));

        assertThat(sent).singleElement().extracting(EmailMessage::subject).isEqualTo("Welcome to ShopSphere");
    }

    @Test
    void ignoresEventsWithoutEmail() {
        notifications.onEvent(new PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), NOW));
        notifications.onEvent(new OrderStatusChanged(UUID.randomUUID(), "SS-1", "petro@example.com", OrderStatus.PAID,
                OrderStatus.PROCESSING, null, NOW));

        assertThat(sent).isEmpty();
    }

    @Test
    void failedEmailDoesNotBreakTheBusinessOperation() {
        NotificationService failing = new NotificationService(List.of(new WelcomeEmailComposer()), message -> {
            throw new IllegalStateException("SMTP is down");
        });

        failing.onEvent(new UserRegistered(UUID.randomUUID(), "ivan@example.com", "Ivan", NOW));
    }
}
