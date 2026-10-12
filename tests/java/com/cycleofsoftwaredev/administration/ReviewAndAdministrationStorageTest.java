package com.cycleofsoftwaredev.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.application.AuditLogService;
import com.cycleofsoftwaredev.administration.application.SettingsService;
import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.delivery.api.DeliveryApi;
import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.identity.api.UserRegistered;
import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewRepository;
import com.cycleofsoftwaredev.reviews.domain.ReviewStatus;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reviews and Administration modules with the real PostgreSQL started by Testcontainers. */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class ReviewAndAdministrationStorageTest {

    /** Records of this test are dated in the future, so they are the newest ones in the shared database. */
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private SettingsService settings;

    @Autowired
    private DeliveryApi delivery;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTestRecordsAndRestoreSettings() {
        jdbc.update("DELETE FROM administration.audit_log WHERE occurred_at >= ?::timestamptz", FAR_FUTURE.toString());
        settings.updateDeliverySettings(new DeliverySettings(Money.of("120.00"), Money.of("80.00"), Money.of("2000.00")));
    }

    @Test
    void reviewIsStoredWithItsModerationStatus() {
        UUID productId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-05T10:00:00Z");
        Review review = reviews.save(new Review(UUID.randomUUID(), productId, UUID.randomUUID(), 4, "Warm and soft", createdAt));

        Review loaded = reviews.findById(review.id()).orElseThrow();
        loaded.approve();
        reviews.save(loaded);

        assertThat(reviews.findByProductId(productId)).singleElement().satisfies(stored -> {
            assertThat(stored.rating()).isEqualTo(4);
            assertThat(stored.text()).isEqualTo("Warm and soft");
            assertThat(stored.createdAt()).isEqualTo(createdAt);
            assertThat(stored.status()).isEqualTo(ReviewStatus.APPROVED);
        });
        assertThat(reviews.findByProductId(UUID.randomUUID())).isEmpty();
    }

    /** Requirement "Write a review": one review per product from one customer, even for simultaneous requests. */
    @Test
    void databaseAllowsOneReviewOfAProductPerCustomer() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-05T10:00:00Z");
        reviews.save(new Review(UUID.randomUUID(), productId, customerId, 5, "First", now));

        assertThatThrownBy(() -> reviews.save(new Review(UUID.randomUUID(), productId, customerId, 1, "Second", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reviews.reviews (id, product_id, customer_id, rating, review_text, created_at, status)
                VALUES (?, ?, ?, 6, '', now(), 'PENDING')""", UUID.randomUUID(), productId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reviews.findByProductId(productId)).hasSize(1);
    }

    /** Requirement "Audit log": the newest records first, cut by the database. */
    @Test
    void auditLogReturnsTheNewestRecordsFirst() {
        for (int hour = 1; hour <= 4; hour++) {
            auditLog.record(new UserRegistered(UUID.randomUUID(), "user" + hour + "@example.com", "User " + hour,
                    FAR_FUTURE.plus(Duration.ofHours(hour))));
        }

        assertThat(auditLog.latestEntries(3)).extracting(AuditEntry::occurredAt).containsExactly(
                FAR_FUTURE.plus(Duration.ofHours(4)), FAR_FUTURE.plus(Duration.ofHours(3)), FAR_FUTURE.plus(Duration.ofHours(2)));
        assertThat(auditLog.latestEntries(1)).singleElement().satisfies(entry -> {
            assertThat(entry.eventType()).isEqualTo("UserRegistered");
            assertThat(entry.details()).contains("user4@example.com");
        });
    }

    /** Requirement "Store settings": a changed price is used by the next order without a restart. */
    @Test
    void changedDeliveryPricesAreStoredInTheSingleRowAndUsedByTheDeliveryModule() {
        settings.updateDeliverySettings(new DeliverySettings(Money.of("150.50"), Money.of("95.00"), Money.of("3000.00")));

        assertThat(settings.currentSettings().storeName()).isEqualTo("ShopSphere");
        assertThat(settings.deliverySettings())
                .isEqualTo(new DeliverySettings(Money.of("150.50"), Money.of("95.00"), Money.of("3000.00")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administration.store_settings", Integer.class)).isEqualTo(1);
        assertThat(delivery.calculateCost(DeliveryMethod.COURIER, Money.of("2500.00"))).isEqualTo(Money.of("150.50"));
        assertThat(delivery.calculateCost(DeliveryMethod.NOVA_POSHTA, Money.of("2999.99"))).isEqualTo(Money.of("95.00"));
        assertThat(delivery.calculateCost(DeliveryMethod.NOVA_POSHTA, Money.of("3000.00"))).isEqualTo(Money.zero());
    }

    @Test
    void databaseRefusesASecondRowOfSettings() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO administration.store_settings (id, store_name, courier_price, nova_poshta_price, free_shipping_threshold)
                VALUES (2, 'Second store', 1, 1, 1)"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
