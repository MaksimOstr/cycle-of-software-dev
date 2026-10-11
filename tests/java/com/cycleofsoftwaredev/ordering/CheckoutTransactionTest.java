package com.cycleofsoftwaredev.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cycleofsoftwaredev.cart.application.CartService;
import com.cycleofsoftwaredev.cart.application.PromoCodeService;
import com.cycleofsoftwaredev.cart.domain.PromoCodeRepository;
import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.inventory.domain.ReservationRepository;
import com.cycleofsoftwaredev.inventory.domain.ReservationStatus;
import com.cycleofsoftwaredev.inventory.domain.StockReservation;
import com.cycleofsoftwaredev.ordering.api.OrderStatus;
import com.cycleofsoftwaredev.ordering.application.CheckoutResult;
import com.cycleofsoftwaredev.ordering.application.CheckoutService;
import com.cycleofsoftwaredev.ordering.application.OrderService;
import com.cycleofsoftwaredev.ordering.application.PlaceOrderCommand;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.Order;
import com.cycleofsoftwaredev.ordering.domain.OrderRepository;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.payment.application.PaymentService;
import com.cycleofsoftwaredev.payment.application.PaymentWebhookEvent;
import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentRepository;
import com.cycleofsoftwaredev.payment.domain.PaymentStatus;
import com.cycleofsoftwaredev.payment.infrastructure.SimulatedPaymentProvider;
import com.cycleofsoftwaredev.shared.domain.Actor;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Interaction of the Ordering, Cart, Inventory and Payment modules in real database transactions
 * (PostgreSQL in Testcontainers). The payment provider is the only part that is replaced: the test decides
 * when it fails and sees what was asked from it.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class CheckoutTransactionTest {

    @MockitoSpyBean
    private SimulatedPaymentProvider paymentProvider;

    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private CartService carts;

    @Autowired
    private PromoCodeService promoCodes;

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private CheckoutService checkout;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private PaymentService payments;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID customerId = UUID.randomUUID();
    private String suffix;
    private UUID variantId;
    private UUID cartId;

    @BeforeEach
    void putAProductIntoTheCart() {
        suffix = Long.toString(System.nanoTime());
        UUID categoryId = categories.createCategory("Checkout " + suffix, null).id();
        Product product = products.createProduct("Kettle " + suffix, "", categoryId);
        variantId = products.addVariant(product.id(), "KET-" + suffix, "white", Money.of("1000.00")).id();
        inventory.setStock(variantId, 5);
        cartId = carts.createCart(customerId).cartId();
        carts.addItem(cartId, variantId, 2);
    }

    /** NFR "Order and payment integrity": checkout either does everything or changes nothing. */
    @Test
    void checkoutThatFailsAtThePaymentProviderLeavesNoOrderNoReservationAndAFullCart() {
        String promoCode = "SAVE" + suffix;
        promoCodes.createPromoCode(promoCode, 10, Instant.now().plus(Duration.ofDays(1)), 1);
        carts.applyPromoCode(cartId, promoCode);
        String idempotencyKey = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("The payment provider does not answer"))
                .when(paymentProvider).createCheckoutSession(any());

        assertThatThrownBy(() -> checkout.placeOrder(command(PaymentMethod.CARD_ONLINE), idempotencyKey))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.findByIdempotencyKey(idempotencyKey)).isEmpty();
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(5);
        assertThat(carts.getSnapshot(cartId).lines()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(2));
        assertThat(promoCodeRepository.findByCode(promoCode).orElseThrow().usedCount()).isZero();

        // the customer presses "Pay" again when the provider works: the same request now succeeds
        doCallRealMethod().when(paymentProvider).createCheckoutSession(any());
        CheckoutResult result = checkout.placeOrder(command(PaymentMethod.CARD_ONLINE), idempotencyKey);

        assertThat(result.total()).isEqualTo(Money.of("1880.00")); // 2000 - 10% + 80 Nova Poshta
        assertThat(result.paymentUrl()).isNotBlank();
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(3);
        assertThat(carts.getSnapshot(cartId).isEmpty()).isTrue();
        assertThat(promoCodeRepository.findByCode(promoCode).orElseThrow().usedCount()).isEqualTo(1);
    }

    @Test
    void paymentNotificationMarksTheOrderPaidAndTakesTheGoodsFromStock() {
        CheckoutResult result = checkout.placeOrder(command(PaymentMethod.CARD_ONLINE), UUID.randomUUID().toString());
        Payment payment = paymentRepository.findByOrderId(result.orderId()).orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.amount()).isEqualTo(result.total());

        PaymentWebhookEvent paid = new PaymentWebhookEvent("evt_" + suffix, PaymentWebhookEvent.CHECKOUT_COMPLETED,
                payment.providerSessionId());
        payments.handleWebhook(paid);
        payments.handleWebhook(paid); // the provider repeats the notification

        Order order = orders.findById(result.orderId()).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.history()).hasSize(2);
        assertThat(paymentRepository.findByOrderId(result.orderId()).orElseThrow().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reservations.findByOrderId(result.orderId())).extracting(StockReservation::status)
                .containsExactly(ReservationStatus.COMMITTED);
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(3);
    }

    /** Requirement "Stock reservation": an unpaid card order is cancelled after 30 minutes. */
    @Test
    void expiryJobCancelsTheUnpaidOrderReleasesTheStockAndClosesThePayment() {
        CheckoutResult result = checkout.placeOrder(command(PaymentMethod.CARD_ONLINE), UUID.randomUUID().toString());

        orderService.expireUnpaidOrders();
        assertThat(orders.findById(result.orderId()).orElseThrow().status()).isEqualTo(OrderStatus.NEW);

        // 31 minutes later
        jdbc.update("UPDATE ordering.orders SET payment_deadline = now() - interval '1 minute' WHERE id = ?", result.orderId());
        orderService.expireUnpaidOrders();

        Order order = orders.findById(result.orderId()).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(5);
        assertThat(reservations.findByOrderId(result.orderId())).extracting(StockReservation::status)
                .containsExactly(ReservationStatus.RELEASED);
        assertThat(paymentRepository.findByOrderId(result.orderId()).orElseThrow().status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void moneyThatArrivesAfterTheCancellationIsReturnedToTheCustomer() {
        CheckoutResult result = checkout.placeOrder(command(PaymentMethod.CARD_ONLINE), UUID.randomUUID().toString());
        String sessionId = paymentRepository.findByOrderId(result.orderId()).orElseThrow().providerSessionId();
        orderService.cancelOrder(result.orderId(), Actor.customer(customerId), "Changed my mind");
        verify(paymentProvider, never()).refund(eq(sessionId), any());

        payments.handleWebhook(new PaymentWebhookEvent("evt_late_" + suffix, PaymentWebhookEvent.CHECKOUT_COMPLETED, sessionId));

        assertThat(orders.findById(result.orderId()).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.findByOrderId(result.orderId()).orElseThrow().status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(5);
        verify(paymentProvider).refund(sessionId, result.total());
    }

    @Test
    void cashOrderNeedsNoPaymentAndTheManagerConfirmsIt() {
        CheckoutResult result = checkout.placeOrder(command(PaymentMethod.CASH_ON_DELIVERY), UUID.randomUUID().toString());

        assertThat(result.paymentUrl()).isNull();
        assertThat(paymentRepository.findByOrderId(result.orderId())).isEmpty();

        orderService.changeStatus(result.orderId(), OrderStatus.PROCESSING, Actor.staff(UUID.randomUUID()), null);

        assertThat(reservations.findByOrderId(result.orderId())).extracting(StockReservation::status)
                .containsExactly(ReservationStatus.COMMITTED);
        assertThat(inventory.availableQuantity(variantId)).isEqualTo(3);
    }

    private PlaceOrderCommand command(PaymentMethod paymentMethod) {
        return new PlaceOrderCommand(cartId, customerId,
                new ContactInfo("Petro Customer", "petro" + suffix + "@example.com", "+380501234567"),
                DeliveryMethod.NOVA_POSHTA, "Kyiv, branch 12", paymentMethod);
    }
}
