package com.cycleofsoftwaredev.integration;

import com.cycleofsoftwaredev.support.TestInfrastructure;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.notification.domain.EmailMessage;
import com.cycleofsoftwaredev.notification.domain.EmailSender;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration of all modules of the modular monolith (Laboratory Work 5). The scenarios go through the REST API
 * only and check every integration point: Web -> Identity, Catalog -> Inventory, Cart -> Catalog and Inventory,
 * Ordering -> Cart, Delivery, Inventory and Payment, Delivery -> Administration (settings),
 * Reviews -> Ordering, Notification and Audit log <- domain events of the other modules.
 */
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
@AutoConfigureMockMvc
class SystemIntegrationTest {

    private static final String USER = "X-User-Id";

    /** Replaces the email adapter, so the test can see what the Notification module has sent. */
    @TestConfiguration
    static class RecordingEmail {

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    static class RecordingEmailSender implements EmailSender {

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }

        List<String> subjectsFor(String email) {
            return sent.stream().filter(message -> message.to().equals(email)).map(EmailMessage::subject).toList();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registration;

    @Autowired
    private RecordingEmailSender emails;

    private String suffix;
    private String adminId;
    private String managerId;
    private String customerId;
    private String customerEmail;
    private String productId;
    private String variantId;

    @BeforeEach
    void prepareStore() throws Exception {
        suffix = Long.toString(System.nanoTime());
        // the first administrator is created when the store is installed; everything else is done through the API
        adminId = registration.createUser(new RegisterUserCommand("owner" + suffix + "@example.com", "Store Owner",
                "Owner12345"), Role.ADMINISTRATOR).id().toString();

        // Identity + Administration: a registered user becomes a manager
        managerId = register("manager" + suffix + "@example.com", "Olena Manager", "Manager12345");
        mvc.perform(json(put("/api/v1/admin/users/" + managerId + "/role"), "{\"role\":\"MANAGER\"}").header(USER, adminId))
                .andExpect(status().isOk());

        customerEmail = "customer" + suffix + "@example.com";
        customerId = register(customerEmail, "Petro Customer", "Customer12345");
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + customerEmail + "\",\"password\":\"Customer12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId));

        // Catalog + Inventory: the manager fills the catalog and the stock
        String categoryId = read(mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Kitchen " + suffix + "\"}")
                .header(USER, managerId)).andExpect(status().isCreated()), "$.id");
        productId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"Kettle " + suffix + "\",\"description\":\"Electric kettle 1.7 l\",\"categoryId\":\"" + categoryId + "\"}")
                .header(USER, managerId)).andExpect(status().isCreated()), "$.id");
        variantId = read(mvc.perform(json(post("/api/v1/management/products/" + productId + "/variants"),
                        "{\"sku\":\"KET-" + suffix + "\",\"name\":\"white\",\"price\":1000.00}")
                .header(USER, managerId)).andExpect(status().isCreated()), "$.variants[0].id");
        mvc.perform(json(put("/api/v1/management/stock/" + variantId), "{\"onHand\":10}").header(USER, managerId))
                .andExpect(status().isOk());

        // Administration -> Delivery: prices are configured without changing the code
        mvc.perform(json(put("/api/v1/admin/settings/delivery"),
                        "{\"courierPrice\":100.00,\"novaPoshtaPrice\":70.00,\"freeShippingThreshold\":5000.00}").header(USER, adminId))
                .andExpect(status().isOk());
    }

    /** The settings are stored in the database shared with the other test classes. */
    @AfterEach
    void restoreDeliverySettings() throws Exception {
        mvc.perform(json(put("/api/v1/admin/settings/delivery"),
                        "{\"courierPrice\":120.00,\"novaPoshtaPrice\":80.00,\"freeShippingThreshold\":2000.00}").header(USER, adminId))
                .andExpect(status().isOk());
    }

    @Test
    void cashOrderGoesThroughAllModulesFromCatalogToReview() throws Exception {
        String promoCode = "SAVE" + suffix;
        mvc.perform(json(post("/api/v1/management/promo-codes"), "{\"code\":\"" + promoCode + "\",\"discountPercent\":10,"
                        + "\"validUntil\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\",\"usageLimit\":5}").header(USER, managerId))
                .andExpect(status().isCreated());

        // Catalog: the customer finds the product
        mvc.perform(get("/api/v1/products").param("query", "kettle " + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(productId))
                .andExpect(jsonPath("$.items[0].variants[0].availableQuantity").value(10));

        // Cart -> Catalog (price) and Inventory (available quantity)
        String cartId = read(mvc.perform(post("/api/v1/carts").header(USER, customerId)).andExpect(status().isCreated()), "$.cartId");
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"), "{\"variantId\":\"" + variantId + "\",\"quantity\":11}"))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"), "{\"variantId\":\"" + variantId + "\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal.amount").value(2000.0));
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/promo-code"), "{\"code\":\"" + promoCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount.amount").value(200.0));

        // Ordering -> Cart, Delivery (cost from the settings), Inventory (reservation)
        String orderNumber = read(mvc.perform(json(post("/api/v1/orders"), order(cartId, "COURIER", "CASH_ON_DELIVERY"))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).header(USER, customerId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total.amount").value(1900.0)), "$.orderNumber"); // 2000 - 10% + 100 delivery
        mvc.perform(get("/api/v1/products/" + productId))
                .andExpect(jsonPath("$.variants[0].availableQuantity").value(8));
        mvc.perform(get("/api/v1/carts/" + cartId)).andExpect(jsonPath("$.lines.length()").value(0));

        // a review is possible only after the product is received (Reviews -> Ordering)
        String review = "{\"rating\":5,\"text\":\"Boils fast\"}";
        mvc.perform(json(post("/api/v1/products/" + productId + "/reviews"), review).header(USER, customerId))
                .andExpect(status().isConflict());

        // Management panel: the manager finds the order and processes it
        mvc.perform(get("/api/v1/management/orders").param("search", customerEmail).header(USER, managerId))
                .andExpect(jsonPath("$[0].number").value(orderNumber));
        changeStatus(orderNumber, "{\"status\":\"PROCESSING\"}");
        changeStatus(orderNumber, "{\"status\":\"SHIPPED\",\"trackingNumber\":\"20450000000777\"}");
        changeStatus(orderNumber, "{\"status\":\"DELIVERED\"}");
        mvc.perform(get("/api/v1/me/orders").header(USER, customerId))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[0].trackingNumber").value("20450000000777"))
                .andExpect(jsonPath("$[0].history.length()").value(4));

        // Reviews: published only after moderation
        String reviewId = read(mvc.perform(json(post("/api/v1/products/" + productId + "/reviews"), review).header(USER, customerId))
                .andExpect(status().isCreated()), "$.id");
        mvc.perform(get("/api/v1/products/" + productId + "/reviews")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(json(patch("/api/v1/management/reviews/" + reviewId), "{\"approved\":true}").header(USER, managerId))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(5));

        // Notification <- events of Identity and Ordering
        assertThat(emails.subjectsFor(customerEmail)).containsExactly("Welcome to ShopSphere",
                "Order " + orderNumber + " created", "Order " + orderNumber + ": shipped", "Order " + orderNumber + ": delivered");

        // Administration <- Ordering: sales dashboard and audit log
        Instant now = Instant.now();
        mvc.perform(get("/api/v1/admin/sales").param("from", now.minus(1, ChronoUnit.HOURS).toString())
                        .param("to", now.plus(1, ChronoUnit.HOURS).toString()).header(USER, adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.ordersCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.previous.ordersCount").value(0));
        mvc.perform(get("/api/v1/admin/audit-log").param("limit", "200").header(USER, adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'UserRegistered')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.eventType == 'UserAccountChanged')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.eventType == 'OrderPlaced')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.eventType == 'OrderStatusChanged')]").isNotEmpty());
    }

    @Test
    void cardOrderIsPaidOnceAndRefundedWhenCancelled() throws Exception {
        String cartId = read(mvc.perform(post("/api/v1/carts").header(USER, customerId)).andExpect(status().isCreated()), "$.cartId");
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"), "{\"variantId\":\"" + variantId + "\",\"quantity\":3}"))
                .andExpect(status().isOk());

        // Ordering -> Payment: a payment session is created for the order
        String idempotencyKey = UUID.randomUUID().toString();
        String checkout = mvc.perform(json(post("/api/v1/orders"), order(cartId, "STORE_PICKUP", "CARD_ONLINE"))
                        .header("Idempotency-Key", idempotencyKey).header(USER, customerId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total.amount").value(3000.0))
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(checkout, "$.orderNumber");
        String paymentUrl = JsonPath.read(checkout, "$.paymentUrl");
        String sessionId = paymentUrl.substring(paymentUrl.lastIndexOf('/') + 1);

        // NFR "Order and payment integrity": a repeated request and a repeated webhook change nothing
        mvc.perform(json(post("/api/v1/orders"), order(cartId, "STORE_PICKUP", "CARD_ONLINE"))
                        .header("Idempotency-Key", idempotencyKey).header(USER, customerId))
                .andExpect(jsonPath("$.orderNumber").value(orderNumber));
        String webhook = "{\"id\":\"evt_" + suffix + "\",\"type\":\"checkout.session.completed\",\"sessionId\":\"" + sessionId + "\"}";
        mvc.perform(json(post("/api/v1/payments/webhook"), webhook)).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/payments/webhook"), webhook)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/" + orderNumber).header(USER, customerId))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(get("/api/v1/management/orders").param("search", customerEmail).header(USER, managerId))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(7));

        // Payment -> Ordering -> Inventory: cancellation returns the goods to the stock
        mvc.perform(post("/api/v1/orders/" + orderNumber + "/cancel").header(USER, customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(10));
        assertThat(emails.subjectsFor(customerEmail)).contains("Order " + orderNumber + " created",
                "Order " + orderNumber + ": paid", "Order " + orderNumber + ": cancelled");
    }

    private String register(String email, String fullName, String password) throws Exception {
        return read(mvc.perform(json(post("/api/v1/auth/register"),
                        "{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated()), "$.id");
    }

    private String order(String cartId, String deliveryMethod, String paymentMethod) {
        return """
                {"cartId":"%s","fullName":"Petro Customer","email":"%s","phone":"+380501234567",
                 "deliveryMethod":"%s","deliveryAddress":"Kyiv, Khreshchatyk 1","paymentMethod":"%s"}
                """.formatted(cartId, customerEmail, deliveryMethod, paymentMethod);
    }

    private void changeStatus(String orderNumber, String body) throws Exception {
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"), body).header(USER, managerId))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
