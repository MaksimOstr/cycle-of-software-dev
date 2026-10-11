package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.support.TestInfrastructure;
import com.cycleofsoftwaredev.web.security.TokenService;
import org.springframework.context.annotation.Import;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cycleofsoftwaredev.identity.api.PasswordResetRequested;
import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end scenario through the REST API: catalog, cart, checkout, payment webhook, order management. */
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
@AutoConfigureMockMvc
@RecordApplicationEvents
class ShopApiIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private RegistrationService registration;

    @Autowired
    private TokenService tokens;

    private final Map<String, String> productIds = new HashMap<>();
    private UserView manager;
    private UserView customer;

    @BeforeEach
    void createUsers() {
        String suffix = Long.toString(System.nanoTime());
        manager = registration.createUser(
                new RegisterUserCommand("manager" + suffix + "@example.com", "Manager", "Manager123"), Role.MANAGER);
        customer = registration.register(
                new RegisterUserCommand("customer" + suffix + "@example.com", "Customer", "Customer123"));
    }

    @Test
    void customerBuysProductWithCardAndManagerShipsIt() throws Exception {
        String variantId = createProductInStock("SKU-" + System.nanoTime(), "1500.00", 10);

        String cartId = read(mvc.perform(post("/api/v1/carts").header(AUTH, bearer(customer)))
                .andExpect(status().isCreated()), "$.cartId");
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"), "{\"variantId\":\"" + variantId + "\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));

        String order = """
                {"cartId":"%s","fullName":"Petro Customer","email":"petro@example.com","phone":"+380501234567",
                 "deliveryMethod":"NOVA_POSHTA","deliveryAddress":"Kyiv, branch 12","paymentMethod":"CARD_ONLINE"}
                """.formatted(cartId);
        String idempotencyKey = UUID.randomUUID().toString();
        String response = mvc.perform(json(post("/api/v1/orders"), order)
                        .header("Idempotency-Key", idempotencyKey).header(AUTH, bearer(customer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total.amount").value(3000.0)) // 2 x 1500, delivery is free above 2000
                .andExpect(jsonPath("$.total.currency").value("UAH"))
                .andExpect(jsonPath("$.total.zero").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(response, "$.orderNumber");
        String paymentUrl = JsonPath.read(response, "$.paymentUrl");

        // the SPA repeats the request: the same order is returned
        mvc.perform(json(post("/api/v1/orders"), order).header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber));

        String sessionId = paymentUrl.substring(paymentUrl.lastIndexOf('/') + 1);
        mvc.perform(json(post("/api/v1/payments/webhook"),
                        "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/" + orderNumber).header(AUTH, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // a customer may not manage orders, a manager may
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"), "{\"status\":\"PROCESSING\"}")
                        .header(AUTH, bearer(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"), "{\"status\":\"PROCESSING\"}")
                        .header(AUTH, bearer(manager)))
                .andExpect(status().isOk());
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"),
                        "{\"status\":\"SHIPPED\",\"trackingNumber\":\"20450000000001\"}").header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("20450000000001"));

        mvc.perform(get("/api/v1/me/orders").header(AUTH, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(orderNumber));
        mvc.perform(get("/api/v1/products/" + productOf(variantId)))
                .andExpect(jsonPath("$.variants[0].availableQuantity").value(8));
    }

    @Test
    void errorsUseUniformFormat() throws Exception {
        String cartId = read(mvc.perform(post("/api/v1/carts")).andExpect(status().isCreated()), "$.cartId");
        String order = """
                {"cartId":"%s","fullName":"Petro","email":"petro@example.com","phone":"+380501234567",
                 "deliveryMethod":"STORE_PICKUP","paymentMethod":"CASH_ON_DELIVERY"}
                """.formatted(cartId);

        mvc.perform(json(post("/api/v1/orders"), order).header("Idempotency-Key", "empty-cart"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATED"))
                .andExpect(jsonPath("$.message").value("The cart is empty"));
        mvc.perform(json(post("/api/v1/auth/register"), "{\"email\":\"not-an-email\",\"fullName\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
        mvc.perform(get("/api/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Hidden\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestsGetUniformErrors() throws Exception {
        mvc.perform(json(post("/api/v1/auth/login"), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(json(post("/api/v1/orders"), """
                        {"cartId":"%s","fullName":"Petro","email":"petro@example.com","phone":"+380501234567",
                         "deliveryMethod":"TELEPORT","paymentMethod":"CARD_ONLINE"}""".formatted(UUID.randomUUID()))
                        .header("Idempotency-Key", "bad-enum"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(json(post("/api/v1/orders"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"));
        mvc.perform(get("/api/v1/products/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value of parameter 'productId'"));
        mvc.perform(get("/api/v1/admin/sales").header(AUTH, bearer(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/sales").header(AUTH, bearer(newAdministrator())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/categories"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void customerOrderIsVisibleOnlyToItsOwnerAndStaff() throws Exception {
        String variantId = createProductInStock("SKU-" + System.nanoTime(), "500.00", 5);
        String orderNumber = placeOrder(variantId, customer);
        UserView otherCustomer = registration.register(
                new RegisterUserCommand("other" + System.nanoTime() + "@example.com", "Other", "Other12345"));

        mvc.perform(get("/api/v1/orders/" + orderNumber)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + orderNumber).header(AUTH, bearer(otherCustomer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/" + orderNumber).header(AUTH, bearer(customer))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/" + orderNumber).header(AUTH, bearer(manager))).andExpect(status().isOk());
    }

    @Test
    void guestTracksOrderWithTheEmailUsedAtCheckout() throws Exception {
        String variantId = createProductInStock("SKU-" + System.nanoTime(), "500.00", 5);
        String orderNumber = placeOrder(variantId, null);

        mvc.perform(get("/api/v1/orders/" + orderNumber)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + orderNumber).param("email", "someone@example.com"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + orderNumber).param("email", "PETRO@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(orderNumber));
    }

    private String placeOrder(String variantId, UserView user) throws Exception {
        MockHttpServletRequestBuilder createCart = post("/api/v1/carts");
        if (user != null) {
            createCart.header(AUTH, bearer(user));
        }
        String cartId = read(mvc.perform(createCart).andExpect(status().isCreated()), "$.cartId");
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"), "{\"variantId\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());
        MockHttpServletRequestBuilder order = json(post("/api/v1/orders"), """
                {"cartId":"%s","fullName":"Petro","email":"petro@example.com","phone":"+380501234567",
                 "deliveryMethod":"STORE_PICKUP","paymentMethod":"CASH_ON_DELIVERY"}""".formatted(cartId))
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (user != null) {
            order.header(AUTH, bearer(user));
        }
        return read(mvc.perform(order).andExpect(status().isCreated()), "$.orderNumber");
    }

    @Test
    void registersAndLogsIn() throws Exception {
        String email = "new" + System.nanoTime() + "@example.com";
        mvc.perform(json(post("/api/v1/auth/register"),
                        "{\"email\":\"" + email + "\",\"fullName\":\"New Customer\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"wrong1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerEditsHidesAndDeletesProductsAndCategories() throws Exception {
        String name = "Lamp " + System.nanoTime();
        String categoryId = read(mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Lightning\"}")
                .header(AUTH, bearer(manager))).andExpect(status().isCreated()), "$.id");
        String productId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"" + name + "\",\"description\":\"Desk lamp\",\"categoryId\":\"" + categoryId + "\"}")
                .header(AUTH, bearer(manager))).andExpect(status().isCreated()), "$.id");
        mvc.perform(json(post("/api/v1/management/products/" + productId + "/variants"),
                        "{\"sku\":\"LAMP-" + System.nanoTime() + "\",\"name\":\"white\",\"price\":799.00}").header(AUTH, bearer(manager)))
                .andExpect(status().isCreated());

        mvc.perform(json(put("/api/v1/management/categories/" + categoryId), "{\"name\":\"Lighting\"}").header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lighting"));
        mvc.perform(json(put("/api/v1/management/products/" + productId),
                        "{\"name\":\"" + name + " LED\",\"description\":\"LED desk lamp\",\"categoryId\":\"" + categoryId + "\"}")
                        .header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("LED desk lamp"));
        mvc.perform(get("/api/v1/products").param("query", name).param("categoryId", categoryId).param("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.items[0].name").value(name + " LED"))
                .andExpect(jsonPath("$.items[0].variants[0].price.amount").value(799.0));
        mvc.perform(get("/api/v1/products").param("sort", "CHEAPEST")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("page", "0")).andExpect(status().isBadRequest());

        // a hidden product disappears from the storefront but stays in the management panel
        mvc.perform(json(patch("/api/v1/management/products/" + productId + "/visibility"), "{\"visible\":false}")
                        .header(AUTH, bearer(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/management/products/" + productId + "/visibility"), "{\"visible\":false}")
                        .header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products").param("query", name)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/management/products").header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + productId + "')].active").value(false));

        mvc.perform(delete("/api/v1/management/categories/" + categoryId).header(AUTH, bearer(manager)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The category contains products and cannot be deleted"));
        mvc.perform(delete("/api/v1/management/products/" + productId).header(AUTH, bearer(manager)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/management/categories/" + categoryId).header(AUTH, bearer(manager)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/management/categories/" + categoryId).header(AUTH, bearer(manager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerFindsOrdersInTheOrderList() throws Exception {
        String variantId = createProductInStock("SKU-" + System.nanoTime(), "500.00", 5);
        String orderNumber = placeOrder(variantId, customer);

        mvc.perform(get("/api/v1/management/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/management/orders").header(AUTH, bearer(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/management/orders").param("search", orderNumber).header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(orderNumber))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].email").value("petro@example.com"))
                .andExpect(jsonPath("$[0].total.amount").value(500.0));
        mvc.perform(get("/api/v1/management/orders").param("status", "NEW").param("paymentMethod", "CASH_ON_DELIVERY")
                        .param("deliveryMethod", "STORE_PICKUP").param("from", "2020-01-01T00:00:00Z").header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.number == '" + orderNumber + "')]").isNotEmpty());
        mvc.perform(get("/api/v1/management/orders").param("search", orderNumber).param("status", "SHIPPED")
                        .header(AUTH, bearer(manager)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/management/orders").param("status", "LOST").header(AUTH, bearer(manager)))
                .andExpect(status().isBadRequest());

        // the phone is found by its digits; the newest order comes first and the page has the requested size
        mvc.perform(get("/api/v1/management/orders").param("search", "050 123-45").param("size", "1")
                        .header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(orderNumber));
        mvc.perform(get("/api/v1/management/orders").param("search", orderNumber).param("page", "2")
                        .header(AUTH, bearer(manager)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/management/orders").param("search", "100%_'; --").header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/management/orders").param("size", "0").header(AUTH, bearer(manager)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administratorManagesUsers() throws Exception {
        UserView admin = newAdministrator();
        String login = "{\"email\":\"" + customer.email() + "\",\"password\":\"Customer123\"}";

        mvc.perform(get("/api/v1/admin/users").header(AUTH, bearer(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").param("query", customer.email()).header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(customer.id().toString()))
                .andExpect(jsonPath("$[0].blocked").value(false));

        // a blocked user can neither log in nor use the API
        mvc.perform(post("/api/v1/admin/users/" + customer.id() + "/block").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(json(post("/api/v1/auth/login"), login))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        mvc.perform(get("/api/v1/me/orders").header(AUTH, bearer(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users/" + customer.id() + "/unblock").header(AUTH, bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/auth/login"), login)).andExpect(status().isOk());

        // the customer becomes a manager and gets access to the management panel
        mvc.perform(get("/api/v1/management/products").header(AUTH, bearer(customer))).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/v1/admin/users/" + customer.id() + "/role"), "{\"role\":\"MANAGER\"}").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
        mvc.perform(get("/api/v1/management/products").header(AUTH, bearer(customer))).andExpect(status().isForbidden());
        String managerToken = "Bearer " + read(mvc.perform(json(post("/api/v1/auth/login"), login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("MANAGER")), "$.accessToken");
        mvc.perform(get("/api/v1/management/products").header(AUTH, managerToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users/" + customer.id()).header(AUTH, bearer(admin)))
                .andExpect(jsonPath("$.role").value("MANAGER"));

        mvc.perform(post("/api/v1/admin/users/" + admin.id() + "/block").header(AUTH, bearer(admin)))
                .andExpect(status().isConflict());

        // the search is done by the database: by a part of the name without case, special characters are literal
        mvc.perform(get("/api/v1/admin/users").param("query", "CUSTOMER").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + customer.id() + "')]").isNotEmpty());
        mvc.perform(get("/api/v1/admin/users").param("query", "%_'; --").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/admin/audit-log").param("limit", "2").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("UserAccountChanged"));
        mvc.perform(get("/api/v1/admin/audit-log").param("limit", "100000").header(AUTH, bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/audit-log").header(AUTH, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'UserAccountChanged')]").isNotEmpty());
    }

    @Test
    void forgottenPasswordIsResetWithTheCodeFromEmail() throws Exception {
        String email = customer.email();
        mvc.perform(json(post("/api/v1/auth/password-reset"), "{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(json(post("/api/v1/auth/password-reset"), "{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        String token = applicationEvents.stream(PasswordResetRequested.class)
                .filter(event -> event.email().equals(email)).findFirst().orElseThrow().token();

        mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), "{\"token\":\"" + token + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        "{\"token\":\"" + token + "\",\"newPassword\":\"Changed12345\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        "{\"token\":\"" + token + "\",\"newPassword\":\"Another12345\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATED"));

        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"Customer123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"Changed12345\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void accountIsLockedAfterFiveFailedLogins() throws Exception {
        String wrong = "{\"email\":\"" + customer.email() + "\",\"password\":\"wrong1234\"}";
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(json(post("/api/v1/auth/login"), wrong)).andExpect(status().isUnauthorized());
        }

        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + customer.email() + "\",\"password\":\"Customer123\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    private String createProductInStock(String sku, String price, int quantity) throws Exception {
        String categoryId = read(mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Hoodies\"}")
                .header(AUTH, bearer(manager))).andExpect(status().isCreated()), "$.id");
        String productId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"Hoodie\",\"description\":\"Cotton\",\"categoryId\":\"" + categoryId + "\"}")
                .header(AUTH, bearer(manager))).andExpect(status().isCreated()), "$.id");
        String variantId = read(mvc.perform(json(post("/api/v1/management/products/" + productId + "/variants"),
                        "{\"sku\":\"" + sku + "\",\"name\":\"M\",\"price\":" + price + "}")
                .header(AUTH, bearer(manager))).andExpect(status().isCreated()), "$.variants[0].id");
        mvc.perform(json(put("/api/v1/management/stock/" + variantId), "{\"onHand\":" + quantity + "}")
                        .header(AUTH, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(quantity));
        productIds.put(variantId, productId);
        return variantId;
    }

    /** Value of the Authorization header with a fresh access token of the user. */
    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(user).accessToken();
    }

    private UserView newAdministrator() {
        return registration.createUser(
                new RegisterUserCommand("admin" + System.nanoTime() + "@example.com", "Admin", "Admin12345"), Role.ADMINISTRATOR);
    }

    private String productOf(String variantId) {
        return productIds.get(variantId);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
