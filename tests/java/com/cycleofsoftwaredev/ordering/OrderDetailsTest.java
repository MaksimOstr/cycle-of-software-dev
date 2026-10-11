package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.DeliveryInfo;
import com.cycleofsoftwaredev.ordering.domain.OrderFilter;
import com.cycleofsoftwaredev.ordering.domain.OrderItem;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Value objects of an order: contacts, delivery, items, and the numbers of the sales statistics. */
class OrderDetailsTest {

    /** Requirement "Checkout": a guest must provide name, email and phone number. */
    @ParameterizedTest
    @ValueSource(strings = {"+380501234567", "0501234567", "+38 (050) 123-45-67", "050 123 45 67"})
    void acceptsPhoneNumbersInTheUsualFormats(String phone) {
        assertThat(new ContactInfo("Petro", "petro@example.com", phone).phone()).isEqualTo(phone);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345", "phone number", "+380501234567890123456", "+38050123456a"})
    void rejectsWhatIsNotAPhoneNumber(String phone) {
        assertThatThrownBy(() -> new ContactInfo("Petro", "petro@example.com", phone))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "petro", "petro@", "@example.com", "petro@example", "petro @example.com", "a@b@c.com"})
    void rejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> new ContactInfo("Petro", email, "+380501234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameIsRequiredAndTrimmed() {
        assertThat(new ContactInfo("  Petro Customer ", "petro@example.com", "+380501234567").fullName())
                .isEqualTo("Petro Customer");
        assertThatThrownBy(() -> new ContactInfo(" ", "petro@example.com", "+380501234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactInfo(null, "petro@example.com", "+380501234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addressIsRequiredForEveryDeliveryMethodExceptPickup() {
        assertThat(DeliveryInfo.of(DeliveryMethod.STORE_PICKUP, null).address()).isNull();
        assertThat(DeliveryInfo.of(DeliveryMethod.NOVA_POSHTA, "Kyiv, branch 12").trackingNumber()).isNull();
        assertThatThrownBy(() -> DeliveryInfo.of(DeliveryMethod.COURIER, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryInfo.of(DeliveryMethod.NOVA_POSHTA, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryInfo.of(null, "Kyiv")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void trackingNumberIsAddedWithoutChangingTheAddress() {
        DeliveryInfo delivery = DeliveryInfo.of(DeliveryMethod.COURIER, "Kyiv, Khreshchatyk 1");

        DeliveryInfo shipped = delivery.withTrackingNumber("20450000000001");

        assertThat(shipped).isEqualTo(new DeliveryInfo(DeliveryMethod.COURIER, "Kyiv, Khreshchatyk 1", "20450000000001"));
        assertThat(delivery.trackingNumber()).isNull();
    }

    @Test
    void lineTotalIsThePriceMultipliedByTheQuantity() {
        OrderItem item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "HOOD-M", "Hoodie (M)", Money.of("199.99"), 3);

        assertThat(item.lineTotal()).isEqualTo(Money.of("599.97"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void orderedQuantityMustBePositive(int quantity) {
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "HOOD-M", "Hoodie", Money.of("10"), quantity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Requirement "Sales dashboard": number of orders, revenue and average order value. */
    @ParameterizedTest
    @CsvSource({"4, 1000.00, 250.00", "3, 100.00, 33.33", "3, 200.00, 66.67", "1, 0.01, 0.01", "0, 0.00, 0.00"})
    void averageOrderValueIsRoundedToKopecks(long orders, String revenue, String average) {
        assertThat(new SalesSummary(orders, Money.of(revenue)).averageOrderValue()).isEqualTo(Money.of(average));
    }

    /** Requirement "Order list": search by order number, customer email or phone. */
    @Test
    void searchTextIsTreatedAsAPhoneOnlyWhenItLooksLikeOne() {
        assertThat(filter("+380 (50) 123-45-67").phoneDigits()).isEqualTo("380501234567");
        assertThat(filter("050 123").phoneDigits()).isEqualTo("050123");
        assertThat(filter("12").phoneDigits()).isNull();
        assertThat(filter("SS-261005-00001").phoneDigits()).isNull();
        assertThat(filter("  Petro@Example.com ").text()).isEqualTo("petro@example.com");
        assertThat(filter("  ").text()).isNull();
        assertThat(OrderFilter.all().text()).isNull();
        assertThat(OrderFilter.all().phoneDigits()).isNull();
    }

    private static OrderFilter filter(String search) {
        return new OrderFilter(null, null, null, null, null, search);
    }
}
