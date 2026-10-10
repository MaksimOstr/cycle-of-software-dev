package com.cycleofsoftwaredev.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.application.AuditLogService;
import com.cycleofsoftwaredev.administration.application.SettingsService;
import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.infrastructure.InMemoryAuditLogRepository;
import com.cycleofsoftwaredev.administration.infrastructure.InMemoryStoreSettingsRepository;
import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdministrationTest {

    @Test
    void administratorChangesDeliverySettings() {
        SettingsService settings = new SettingsService(new InMemoryStoreSettingsRepository());
        DeliverySettings updated = new DeliverySettings(Money.of("150"), Money.of("90"), Money.of("3000"));

        settings.updateDeliverySettings(updated);

        assertThat(settings.deliverySettings()).isEqualTo(updated);
        assertThatThrownBy(() -> new DeliverySettings(Money.of("-1"), Money.zero(), Money.zero()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditLogRecordsEventsNewestFirst() {
        AuditLogService auditLog = new AuditLogService(new InMemoryAuditLogRepository());
        auditLog.record(new UserRegistered(UUID.randomUUID(), "a@example.com", "A", Instant.parse("2026-10-05T10:00:00Z")));
        auditLog.record(new UserRegistered(UUID.randomUUID(), "b@example.com", "B", Instant.parse("2026-10-05T11:00:00Z")));

        AuditEntry newest = auditLog.latestEntries(10).getFirst();
        assertThat(newest.eventType()).isEqualTo("UserRegistered");
        assertThat(newest.occurredAt()).isEqualTo(Instant.parse("2026-10-05T11:00:00Z"));
        assertThat(auditLog.latestEntries(1)).hasSize(1);
    }
}
