package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.api.SalesSummary;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.DeliveryInfo;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderFilter;
import com.cycleofsoftwaredev.ordering.domain.OrderItem;
import com.cycleofsoftwaredev.ordering.domain.OrderNumberGenerator;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.ordering.domain.OrderSearch;
import com.cycleofsoftwaredev.ordering.domain.OrderStatusChange;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Ordering module with the real PostgreSQL started by Testcontainers: the order with its items and status
 * history, the unique idempotency key, the order list and the sales statistics computed by SQL.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class OrderStorageTest {

    /** 2 x 1299.00 + 1 x 250.50 - 100.00 discount + 80.00 delivery. */
    private static final Money ORDER_TOTAL = Money.of("2828.50");

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderSearch orderSearch;

    @Autowired
    private OrderNumberGenerator numbers;

    private final UUID customerId = UUID.randomUUID();
    private final Actor manager = Actor.staff(UUID.randomUUID());
    private String email;
    private String phone;

    /** The database is shared with the other test classes, so every test has its own customer. */
    @BeforeEach
    void createCustomer() {
        long unique = System.nanoTime();
        email = "buyer" + unique + "@example.com";
        phone = "+380 (%02d) %03d-%02d-%02d".formatted(unique % 100, unique / 100 % 1000, unique / 100_000 % 100,
                unique / 10_000_000 % 100);
    }

    @Test
    void orderIsReadBackWithItemsTotalsAndStatusHistory() {
        Instant createdAt = ownDay(2019);
        Order order = newOrder(PaymentMethod.CARD_ONLINE, createdAt);
        order.markPaid(createdAt.plus(Duration.ofMinutes(5)));
        order.startProcessing(manager, createdAt.plus(Duration.ofHours(1)));
        order.ship("20450000000001", manager, createdAt.plus(Duration.ofHours(2)));
        orders.save(order);

        Order loaded = orders.findByNumber(order.number()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(order.id());
        assertThat(loaded.customerId()).isEqualTo(customerId);
        assertThat(loaded.contact()).isEqualTo(new ContactInfo("Petro Customer", email, phone));
        assertThat(loaded.delivery())
                .isEqualTo(new DeliveryInfo(DeliveryMethod.NOVA_POSHTA, "Kyiv, branch 12", "20450000000001"));
        assertThat(loaded.items()).isEqualTo(order.items());
        assertThat(loaded.promoCode()).isEqualTo("WELCOME10");
        assertThat(loaded.subtotal()).isEqualTo(Money.of("2848.50"));
        assertThat(loaded.discount()).isEqualTo(Money.of("100.00"));
        assertThat(loaded.deliveryCost()).isEqualTo(Money.of("80.00"));
        assertThat(loaded.total()).isEqualTo(ORDER_TOTAL);
        assertThat(loaded.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(loaded.createdAt()).isEqualTo(createdAt);
        assertThat(loaded.paymentDeadline()).isEqualTo(createdAt.plus(Order.PAYMENT_TIME_LIMIT));
        assertThat(loaded.history()).extracting(OrderStatusChange::toStatus)
                .containsExactly(OrderStatus.NEW, OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED);
        assertThat(loaded.history().get(2).changedBy()).isEqualTo(manager.userId());
        assertThat(loaded.wasPaidOnline()).isTrue();
    }

    @Test
    void laterStatusChangesAreAddedToTheStoredHistory() {
        Instant createdAt = ownDay(2018);
        Order order = newOrder(PaymentMethod.CASH_ON_DELIVERY, createdAt);
        orders.save(order);

        Order loaded = orders.findById(order.id()).orElseThrow();
        loaded.startProcessing(manager, createdAt.plus(Duration.ofHours(1)));
        loaded.ship("20450000000002", manager, createdAt.plus(Duration.ofHours(2)));
        loaded.markDelivered(manager, createdAt.plus(Duration.ofDays(1)));
        orders.save(loaded);

        Order delivered = orders.findById(order.id()).orElseThrow();
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.deliveredAt()).isEqualTo(createdAt.plus(Duration.ofDays(1)));
        assertThat(delivered.history()).hasSize(4);
        assertThat(delivered.paymentDeadline()).isNull();
        assertThat(delivered.isReturnAllowed(createdAt.plus(Duration.ofDays(14)))).isTrue();
        assertThat(delivered.isReturnAllowed(createdAt.plus(Duration.ofDays(15)))).isFalse();
    }

    /** NFR "Order and payment integrity": a repeated checkout request can never create a second order. */
    @Test
    void databaseRejectsASecondOrderWithTheSameIdempotencyKey() {
        String key = UUID.randomUUID().toString();
        Order first = newOrder(PaymentMethod.CASH_ON_DELIVERY, ownDay(2017), key);
        orders.save(first);

        assertThatThrownBy(() -> orders.save(newOrder(PaymentMethod.CASH_ON_DELIVERY, ownDay(2017), key)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(orders.findByIdempotencyKey(key)).map(Order::id).contains(first.id());
        assertThat(orders.findByIdempotencyKey(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void customerSeesOnlyTheirOwnOrders() {
        Order first = orders.save(newOrder(PaymentMethod.CASH_ON_DELIVERY, ownDay(2016)));
        Order second = orders.save(newOrder(PaymentMethod.CASH_ON_DELIVERY, ownDay(2016)));

        assertThat(orders.findByCustomerId(customerId)).extracting(Order::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(orders.findByCustomerId(UUID.randomUUID())).isEmpty();
    }

    /** Requirement "Stock reservation": an unpaid card order is cancelled 30 minutes after it was created. */
    @Test
    void onlyUnpaidCardOrdersBecomeOverdueAfterThePaymentDeadline() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant tenMinutesAgo = now.minus(Duration.ofMinutes(10));
        Order unpaid = orders.save(newOrder(PaymentMethod.CARD_ONLINE, tenMinutesAgo));
        Order cash = orders.save(newOrder(PaymentMethod.CASH_ON_DELIVERY, tenMinutesAgo));
        Order paid = newOrder(PaymentMethod.CARD_ONLINE, tenMinutesAgo);
        paid.markPaid(now);
        orders.save(paid);

        assertThat(orders.findOverdueIds(now)).doesNotContain(unpaid.id(), cash.id(), paid.id());
        assertThat(orders.findOverdueIds(now.plus(Duration.ofMinutes(20))))
                .contains(unpaid.id())
                .doesNotContain(cash.id(), paid.id());
    }

    /** Requirement "Order list": filters, search, newest first, pages cut by the database. */
    @Test
    void orderListIsFilteredSortedAndPagedByTheDatabase() {
        Instant day = ownDay(2005);
        List<Order> saved = new ArrayList<>();
        for (int hour = 1; hour <= 5; hour++) {
            PaymentMethod paymentMethod = hour == 5 ? PaymentMethod.CARD_ONLINE : PaymentMethod.CASH_ON_DELIVERY;
            saved.add(newOrder(paymentMethod, day.plus(Duration.ofHours(hour))));
        }
        saved.get(1).cancel(manager, "Out of stock", day.plus(Duration.ofHours(6)));
        saved.get(4).markPaid(day.plus(Duration.ofHours(6)));
        saved.forEach(orders::save);
        OrderFilter mine = search(null, null, null, null, email);

        assertThat(numbersOf(orderSearch.find(mine, 0, 50))).containsExactly(saved.get(4).number(), saved.get(3).number(),
                saved.get(2).number(), saved.get(1).number(), saved.get(0).number());
        assertThat(numbersOf(orderSearch.find(mine, 2, 2))).containsExactly(saved.get(2).number(), saved.get(1).number());
        assertThat(orderSearch.find(mine, 5, 50)).isEmpty();
        assertThat(numbersOf(orderSearch.find(search(OrderStatus.CANCELLED, null, null, null, email), 0, 50)))
                .containsExactly(saved.get(1).number());
        assertThat(numbersOf(orderSearch.find(search(null, PaymentMethod.CARD_ONLINE, null, null, email), 0, 50)))
                .containsExactly(saved.get(4).number());
        assertThat(orderSearch.find(new OrderFilter(null, null, DeliveryMethod.COURIER, null, null, email), 0, 50)).isEmpty();
        // the period includes its start and excludes its end
        assertThat(numbersOf(orderSearch.find(
                search(null, null, day.plus(Duration.ofHours(2)), day.plus(Duration.ofHours(4)), email), 0, 50)))
                .containsExactly(saved.get(2).number(), saved.get(1).number());
    }

    @Test
    void orderIsFoundByNumberEmailOrPhoneInAnyFormat() {
        Order order = orders.save(newOrder(PaymentMethod.CASH_ON_DELIVERY, ownDay(2006)));
        String digits = phone.replaceAll("\\D", "");

        assertThat(numbersOf(find(order.number().toLowerCase()))).containsExactly(order.number());
        assertThat(numbersOf(find(email.toUpperCase()))).containsExactly(order.number());
        assertThat(numbersOf(find(digits.substring(3)))).contains(order.number());
        assertThat(numbersOf(find(phone))).contains(order.number());
        assertThat(find("nobody" + email)).isEmpty();
        // % and _ are ordinary characters, not wildcards
        assertThat(find("%" + email.substring(5))).isEmpty();
    }

    /** Requirement "Sales dashboard": cancelled and returned orders bring no revenue. */
    @Test
    void salesStatisticsAreComputedByTheDatabaseWithoutCancelledAndReturnedOrders() {
        Instant day = ownDay(2010);
        Order created = newOrder(PaymentMethod.CASH_ON_DELIVERY, day.plus(Duration.ofHours(1)));
        Order processing = newOrder(PaymentMethod.CASH_ON_DELIVERY, day.plus(Duration.ofHours(2)));
        processing.startProcessing(manager, day.plus(Duration.ofHours(3)));
        Order cancelled = newOrder(PaymentMethod.CASH_ON_DELIVERY, day.plus(Duration.ofHours(4)));
        cancelled.cancel(manager, "Changed mind", day.plus(Duration.ofHours(5)));
        Order returned = newOrder(PaymentMethod.CASH_ON_DELIVERY, day.plus(Duration.ofHours(6)));
        returned.startProcessing(manager, day.plus(Duration.ofHours(7)));
        returned.ship("20450000000003", manager, day.plus(Duration.ofHours(8)));
        returned.markDelivered(manager, day.plus(Duration.ofHours(9)));
        returned.markReturned(manager, day.plus(Duration.ofHours(10)));
        List.of(created, processing, cancelled, returned).forEach(orders::save);

        SalesSummary summary = orderSearch.salesSummary(day, day.plus(Duration.ofDays(1)));

        assertThat(summary.ordersCount()).isEqualTo(2);
        assertThat(summary.revenue()).isEqualTo(ORDER_TOTAL.multiply(2));
        assertThat(summary.averageOrderValue()).isEqualTo(ORDER_TOTAL);
        assertThat(orderSearch.salesSummary(day.minus(Duration.ofDays(1)), day))
                .isEqualTo(new SalesSummary(0, Money.zero()));
        // the end of the period is not included
        assertThat(orderSearch.salesSummary(day, day.plus(Duration.ofHours(2))).ordersCount()).isEqualTo(1);
    }

    /** Order numbers come from a database sequence, so they stay unique for several application instances. */
    @Test
    void simultaneousCheckoutsGetDifferentOrderNumbers() throws Exception {
        List<Callable<String>> checkouts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            checkouts.add(numbers::nextNumber);
        }

        Set<String> generated = new HashSet<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(checkouts.size())) {
            for (Future<String> number : executor.invokeAll(checkouts)) {
                generated.add(number.get());
            }
        }

        assertThat(generated).hasSize(30).allMatch(number -> number.matches("SS-\\d{6}-\\d{5,}"));
    }

    private Order newOrder(PaymentMethod paymentMethod, Instant createdAt) {
        return newOrder(paymentMethod, createdAt, UUID.randomUUID().toString());
    }

    private Order newOrder(PaymentMethod paymentMethod, Instant createdAt, String idempotencyKey) {
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "HOOD-M", "Basic Hoodie (M)", Money.of("1299.00"), 2),
                new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "MUG", "Mug (white)", Money.of("250.50"), 1));
        return Order.place(UUID.randomUUID(), numbers.nextNumber(), idempotencyKey, customerId,
                new ContactInfo("Petro Customer", email, phone), DeliveryInfo.of(DeliveryMethod.NOVA_POSHTA, "Kyiv, branch 12"),
                paymentMethod, items, "WELCOME10", Money.of("100.00"), Money.of("80.00"), createdAt);
    }

    /** A day of the given year that no other test of this run uses. */
    private static Instant ownDay(int year) {
        return Instant.parse(year + "-01-01T00:00:00Z").plus(ThreadLocalRandom.current().nextInt(360), ChronoUnit.DAYS);
    }

    private List<Order> find(String text) {
        return orderSearch.find(search(null, null, null, null, text), 0, 50);
    }

    private static OrderFilter search(OrderStatus status, PaymentMethod paymentMethod, Instant from, Instant to, String text) {
        return new OrderFilter(status, paymentMethod, null, from, to, text);
    }

    private static List<String> numbersOf(List<Order> found) {
        return found.stream().map(Order::number).toList();
    }
}
