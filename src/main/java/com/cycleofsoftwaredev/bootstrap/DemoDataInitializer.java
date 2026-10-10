package com.cycleofsoftwaredev.bootstrap;

import com.cycleofsoftwaredev.cart.application.PromoCodeService;
import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fills the in-memory storage with demo data so that the API can be tried right after start.
 * Disabled with {@code shopsphere.demo-data.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "shopsphere.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final RegistrationService registration;
    private final CategoryService categories;
    private final ProductService products;
    private final InventoryService inventory;
    private final PromoCodeService promoCodes;
    private final Clock clock;

    public DemoDataInitializer(RegistrationService registration, CategoryService categories, ProductService products,
                               InventoryService inventory, PromoCodeService promoCodes, Clock clock) {
        this.registration = registration;
        this.categories = categories;
        this.products = products;
        this.inventory = inventory;
        this.promoCodes = promoCodes;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        UserView admin = registration.createUser(
                new RegisterUserCommand("admin@shopsphere.local", "Store Administrator", "Admin12345"), Role.ADMINISTRATOR);
        UserView manager = registration.createUser(
                new RegisterUserCommand("manager@shopsphere.local", "Olena Manager", "Manager12345"), Role.MANAGER);
        UserView customer = registration.register(
                new RegisterUserCommand("customer@shopsphere.local", "Petro Customer", "Customer12345"));

        Category electronics = categories.createCategory("Electronics", null);
        Category smartphones = categories.createCategory("Smartphones", electronics.id());
        Category clothing = categories.createCategory("Clothing", null);

        Product phone = products.createProduct("Galaxy S30", "Smartphone with 6.5\" display", smartphones.id());
        stock(products.addVariant(phone.id(), "GS30-128-BLK", "128 GB, black", Money.of("32999.00")), 15);
        stock(products.addVariant(phone.id(), "GS30-256-SLV", "256 GB, silver", Money.of("36999.00")), 3);

        Product hoodie = products.createProduct("Basic Hoodie", "Cotton hoodie", clothing.id());
        stock(products.addVariant(hoodie.id(), "HOOD-M-GRY", "M, grey", Money.of("1299.00")), 40);
        stock(products.addVariant(hoodie.id(), "HOOD-L-GRY", "L, grey", Money.of("1299.00")), 25);

        promoCodes.createPromoCode("WELCOME10", 10, clock.instant().plus(Duration.ofDays(365)), 1000);

        log.info("Demo data loaded. Use the X-User-Id header: administrator={}, manager={}, customer={}",
                admin.id(), manager.id(), customer.id());
    }

    private void stock(ProductVariant variant, int quantity) {
        inventory.setStock(variant.id(), quantity);
    }
}
