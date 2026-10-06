package com.cycleofsoftwaredev.support;

import com.cycleofsoftwaredev.administration.application.SettingsService;
import com.cycleofsoftwaredev.administration.infrastructure.InMemoryStoreSettingsRepository;
import com.cycleofsoftwaredev.cart.application.CartService;
import com.cycleofsoftwaredev.cart.application.PromoCodeService;
import com.cycleofsoftwaredev.cart.infrastructure.InMemoryCartRepository;
import com.cycleofsoftwaredev.cart.infrastructure.InMemoryPromoCodeRepository;
import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.infrastructure.InMemoryCategoryRepository;
import com.cycleofsoftwaredev.catalog.infrastructure.InMemoryProductRepository;
import com.cycleofsoftwaredev.delivery.api.DeliveryMethod;
import com.cycleofsoftwaredev.delivery.application.CourierCostPolicy;
import com.cycleofsoftwaredev.delivery.application.DeliveryService;
import com.cycleofsoftwaredev.delivery.application.NovaPoshtaCostPolicy;
import com.cycleofsoftwaredev.delivery.application.StorePickupCostPolicy;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.inventory.infrastructure.InMemoryReservationRepository;
import com.cycleofsoftwaredev.inventory.infrastructure.InMemoryStockRepository;
import com.cycleofsoftwaredev.ordering.application.CheckoutService;
import com.cycleofsoftwaredev.ordering.application.OrderQueryService;
import com.cycleofsoftwaredev.ordering.application.OrderService;
import com.cycleofsoftwaredev.ordering.application.PlaceOrderCommand;
import com.cycleofsoftwaredev.ordering.domain.ContactInfo;
import com.cycleofsoftwaredev.ordering.domain.PaymentMethod;
import com.cycleofsoftwaredev.ordering.infrastructure.InMemoryOrderRepository;
import com.cycleofsoftwaredev.ordering.infrastructure.SequentialOrderNumberGenerator;
import com.cycleofsoftwaredev.payment.api.PaymentSucceeded;
import com.cycleofsoftwaredev.payment.application.PaymentService;
import com.cycleofsoftwaredev.payment.infrastructure.InMemoryPaymentRepository;
import com.cycleofsoftwaredev.payment.infrastructure.InMemoryProcessedWebhookEventRepository;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** All modules wired together with in-memory storage, as Spring does it in the application. */
public class ShopFixture {

    public final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T10:00:00Z"));
    public final RecordingEventPublisher events = new RecordingEventPublisher();

    public final CategoryService categories = new CategoryService(new InMemoryCategoryRepository());
    public final ProductService products = new ProductService(new InMemoryProductRepository(), categories);
    public final InventoryService inventory =
            new InventoryService(new InMemoryStockRepository(), new InMemoryReservationRepository());

    private final InMemoryPromoCodeRepository promoCodeRepository = new InMemoryPromoCodeRepository();
    public final PromoCodeService promoCodes = new PromoCodeService(promoCodeRepository);
    public final CartService carts =
            new CartService(new InMemoryCartRepository(), promoCodeRepository, products, inventory, clock);

    public final SettingsService settings = new SettingsService(new InMemoryStoreSettingsRepository());
    public final DeliveryService delivery = new DeliveryService(List.of(new CourierCostPolicy(settings),
            new NovaPoshtaCostPolicy(settings), new StorePickupCostPolicy()));

    public final RecordingPaymentProvider paymentProvider = new RecordingPaymentProvider();
    public final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
    public final PaymentService payments = new PaymentService(paymentRepository,
            new InMemoryProcessedWebhookEventRepository(), paymentProvider, paymentProvider, events, clock);

    public final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    public final CheckoutService checkout = new CheckoutService(orderRepository,
            new SequentialOrderNumberGenerator(clock), carts, delivery, inventory, payments, events, clock);
    public final OrderService orders = new OrderService(orderRepository, inventory, payments, events, clock);
    public final OrderQueryService orderQueries = new OrderQueryService(orderRepository);

    public final UUID customerId = UUID.randomUUID();
    private final Category category = categories.createCategory("Test category", null);

    public ShopFixture() {
        // what Spring does with @EventListener in the application
        events.subscribe(event -> {
            if (event instanceof PaymentSucceeded paymentSucceeded) {
                orders.onPaymentSucceeded(paymentSucceeded);
            }
        });
    }

    /** Creates a product with one variant and puts the given quantity in stock; returns the variant id. */
    public UUID variant(String sku, String price, int stock) {
        Product product = products.createProduct("Product " + sku, "", category.id());
        UUID variantId = products.addVariant(product.id(), sku, "default", Money.of(price)).id();
        inventory.setStock(variantId, stock);
        return variantId;
    }

    public UUID productOf(UUID variantId) {
        return products.findVariant(variantId).orElseThrow().productId();
    }

    public UUID cartWith(UUID variantId, int quantity) {
        UUID cartId = carts.createCart(customerId).cartId();
        carts.addItem(cartId, variantId, quantity);
        return cartId;
    }

    public PlaceOrderCommand order(UUID cartId, PaymentMethod paymentMethod) {
        return new PlaceOrderCommand(cartId, customerId, new ContactInfo("Petro Customer", "petro@example.com", "+380501234567"),
                DeliveryMethod.NOVA_POSHTA, "Kyiv, branch 12", paymentMethod);
    }
}
