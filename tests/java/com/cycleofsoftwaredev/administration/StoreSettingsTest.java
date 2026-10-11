package com.cycleofsoftwaredev.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.application.AuditLogService;
import com.cycleofsoftwaredev.administration.application.SettingsService;
import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.StoreSettings;
import com.cycleofsoftwaredev.administration.infrastructure.InMemoryAuditLogRepository;
import com.cycleofsoftwaredev.administration.infrastructure.InMemoryStoreSettingsRepository;
import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Requirements "Store settings" and "Audit log": the rules of the settings and of a log record. */
class StoreSettingsTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    @Test
    void newStoreStartsWithDefaultDeliveryPrices() {
        StoreSettings settings = StoreSettings.defaults();

        assertThat(settings.storeName()).isEqualTo("ShopSphere");
        assertThat(settings.delivery())
                .isEqualTo(new DeliverySettings(Money.of("120.00"), Money.of("80.00"), Money.of("2000.00")));
        assertThat(new SettingsService(new InMemoryStoreSettingsRepository()).currentSettings().delivery())
                .isEqualTo(settings.delivery());
    }

    @Test
    void storeNameIsRequiredAndTrimmed() {
        StoreSettings settings = StoreSettings.defaults();

        settings.rename("  Sphere Market ");

        assertThat(settings.storeName()).isEqualTo("Sphere Market");
        assertThatThrownBy(() -> settings.rename(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.rename(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.changeDelivery(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deliveryMayBeFreeButNeverNegative() {
        assertThat(new DeliverySettings(Money.zero(), Money.zero(), Money.zero()).courierPrice()).isEqualTo(Money.zero());
        assertThatThrownBy(() -> new DeliverySettings(Money.of("-0.01"), Money.zero(), Money.zero()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliverySettings(Money.zero(), Money.of("-0.01"), Money.zero()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliverySettings(Money.zero(), Money.zero(), Money.of("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliverySettings(null, Money.zero(), Money.zero()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void longDetailsOfAnAuditRecordAreCutToTheSizeOfTheColumn() {
        AuditEntry shortEntry = new AuditEntry(UUID.randomUUID(), "OrderPlaced", "order SS-1", NOW);
        AuditEntry longEntry = new AuditEntry(UUID.randomUUID(), "OrderPlaced", "x".repeat(5000), NOW);

        assertThat(shortEntry.details()).isEqualTo("order SS-1");
        assertThat(longEntry.details()).hasSize(AuditEntry.MAX_DETAILS_LENGTH);
        assertThat(longEntry.eventType()).isEqualTo("OrderPlaced");
        assertThat(longEntry.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void auditRecordKeepsTheTypeAndTheDataOfTheEvent() {
        AuditLogService auditLog = new AuditLogService(new InMemoryAuditLogRepository());
        UUID userId = UUID.randomUUID();

        auditLog.record(new UserRegistered(userId, "petro@example.com", "Petro", NOW));

        assertThat(auditLog.latestEntries(1)).singleElement().satisfies(entry -> {
            assertThat(entry.eventType()).isEqualTo("UserRegistered");
            assertThat(entry.details()).contains(userId.toString(), "petro@example.com");
            assertThat(entry.occurredAt()).isEqualTo(NOW);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, AuditLogService.MAX_ENTRIES + 1})
    void numberOfRequestedAuditRecordsIsLimited(int limit) {
        AuditLogService auditLog = new AuditLogService(new InMemoryAuditLogRepository());

        assertThatThrownBy(() -> auditLog.latestEntries(limit)).isInstanceOf(IllegalArgumentException.class);
        assertThat(auditLog.latestEntries(AuditLogService.MAX_ENTRIES)).isEmpty();
    }
}
