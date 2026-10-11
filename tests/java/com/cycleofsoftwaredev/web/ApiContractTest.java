package com.cycleofsoftwaredev.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cycleofsoftwaredev.identity.api.Role;
import com.cycleofsoftwaredev.identity.api.UserView;
import com.cycleofsoftwaredev.identity.application.RegisterUserCommand;
import com.cycleofsoftwaredev.identity.application.RegistrationService;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import com.cycleofsoftwaredev.web.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Contract of the REST API: the HTTP status and the format of the JSON answer of every group of endpoints,
 * for valid requests and for the requests that must be refused. The application runs on the real PostgreSQL
 * and Redis started by Testcontainers.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
@AutoConfigureMockMvc
class ApiContractTest {

    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registration;

    @Autowired
    private TokenService tokens;

    private String suffix;
    private String manager;
    private String administrator;
    private String customer;
    private String categoryId;
    private String productId;
    private String variantId;

    @BeforeEach
    void createUsersAndOneProduct() throws Exception {
        suffix = Long.toString(System.nanoTime());
        manager = bearer("manager", Role.MANAGER);
        administrator = bearer("admin", Role.ADMINISTRATOR);
        customer = bearer("customer", Role.CUSTOMER);
        categoryId = read(mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Lamps " + suffix + "\"}")
                .header(AUTH, manager)).andExpect(status().isCreated()), "$.id");
        productId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"Desk lamp " + suffix + "\",\"description\":\"LED, 5 W\",\"categoryId\":\"" + categoryId + "\"}")
                .header(AUTH, manager)).andExpect(status().isCreated()), "$.id");
        variantId = read(addVariant(productId, "LAMP-" + suffix, "799.90").andExpect(status().isCreated()), "$.variants[0].id");
        mvc.perform(json(put("/api/v1/management/stock/" + variantId), "{\"onHand\":4}").header(AUTH, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(variantId))
                .andExpect(jsonPath("$.onHand").value(4))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(4))
                .andExpect(jsonPath("$.lowStock").value(true));
    }

    @Test
    void catalogPageHasItemsWithVariantsAndTheNumbersOfThePage() throws Exception {
        mvc.perform(get("/api/v1/products").param("query", "desk lamp " + suffix))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(productId))
                .andExpect(jsonPath("$.items[0].name").value("Desk lamp " + suffix))
                .andExpect(jsonPath("$.items[0].description").value("LED, 5 W"))
                .andExpect(jsonPath("$.items[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].variants[0].id").value(variantId))
                .andExpect(jsonPath("$.items[0].variants[0].sku").value("LAMP-" + suffix))
                .andExpect(jsonPath("$.items[0].variants[0].name").value("white"))
                .andExpect(jsonPath("$.items[0].variants[0].price.amount").value(799.9))
                .andExpect(jsonPath("$.items[0].variants[0].price.currency").value("UAH"))
                .andExpect(jsonPath("$.items[0].variants[0].price.zero").doesNotExist())
                .andExpect(jsonPath("$.items[0].variants[0].availableQuantity").value(4));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + categoryId + "')].name").value(hasItem("Lamps " + suffix)));
        mvc.perform(get("/api/v1/products").param("query", "nothing like " + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void catalogRefusesWrongPagingAndSortingWith400() throws Exception {
        expectError(get("/api/v1/products").param("page", "0"), 400, "BAD_REQUEST");
        expectError(get("/api/v1/products").param("size", "101"), 400, "BAD_REQUEST");
        expectError(get("/api/v1/products").param("size", "ten"), 400, "BAD_REQUEST")
                .andExpect(jsonPath("$.message").value("Invalid value of parameter 'size'"));
        expectError(get("/api/v1/products").param("sort", "CHEAPEST"), 400, "BAD_REQUEST");
        expectError(get("/api/v1/products").param("categoryId", "lamps"), 400, "BAD_REQUEST");
        expectError(get("/api/v1/products").param("categoryId", UUID.randomUUID().toString()), 404, "NOT_FOUND");
    }

    @Test
    void cartIsReturnedWithLinesAndTotalsAndRefusesImpossibleQuantities() throws Exception {
        String cartId = read(mvc.perform(post("/api/v1/carts"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.subtotal.amount").value(0.0))
                .andExpect(jsonPath("$.discount.amount").value(0.0))
                .andExpect(jsonPath("$.promoCode").isEmpty())
                .andExpect(jsonPath("$.empty").doesNotExist()), "$.cartId");
        String items = "/api/v1/carts/" + cartId + "/items";

        mvc.perform(json(post(items), "{\"variantId\":\"" + variantId + "\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cartId))
                .andExpect(jsonPath("$.lines[0].variantId").value(variantId))
                .andExpect(jsonPath("$.lines[0].productId").value(productId))
                .andExpect(jsonPath("$.lines[0].sku").value("LAMP-" + suffix))
                .andExpect(jsonPath("$.lines[0].productName").value("Desk lamp " + suffix + " (white)"))
                .andExpect(jsonPath("$.lines[0].unitPrice.amount").value(799.9))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.subtotal.amount").value(1599.8))
                .andExpect(jsonPath("$.subtotal.currency").value("UAH"));

        expectError(json(post(items), "{\"variantId\":\"" + variantId + "\",\"quantity\":0}"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        expectError(json(post(items), "{\"variantId\":\"" + variantId + "\",\"quantity\":100}"), 400, "VALIDATION_FAILED");
        expectError(json(post(items), "{\"quantity\":1}"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("variantId"));
        expectError(json(put(items + "/" + variantId), "{\"quantity\":-1}"), 400, "VALIDATION_FAILED");
        expectError(json(post(items), "{\"variantId\":\"" + variantId + "\",\"quantity\":3}"), 409, "BUSINESS_RULE_VIOLATED")
                .andExpect(jsonPath("$.message").value(containsString("Only 4 item(s) left")));
        expectError(json(post(items), "{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"), 404, "NOT_FOUND");
        expectError(json(post("/api/v1/carts/" + cartId + "/promo-code"), "{\"code\":\"NO-SUCH-CODE\"}"), 409, "BUSINESS_RULE_VIOLATED");
        expectError(json(post("/api/v1/carts/" + cartId + "/promo-code"), "{\"code\":\" \"}"), 400, "VALIDATION_FAILED");
        expectError(get("/api/v1/carts/" + UUID.randomUUID()), 404, "NOT_FOUND");
        // none of the refused requests has changed the cart
        mvc.perform(get("/api/v1/carts/" + cartId)).andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    /** Requirement "Checkout": name, email and phone of the customer are validated. */
    @Test
    void checkoutRefusesWrongContactsAndLeavesTheCartAndTheStockAsTheyWere() throws Exception {
        String cartId = read(mvc.perform(post("/api/v1/carts")).andExpect(status().isCreated()), "$.cartId");
        mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"), "{\"variantId\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());

        expectError(checkout(cartId, "Petro", "petro@example.com", "----------", "STORE_PICKUP", null), 400, "BAD_REQUEST")
                .andExpect(jsonPath("$.message").value(containsString("Invalid phone number")));
        expectError(checkout(cartId, "Petro", "petro@example.com", "12345", "STORE_PICKUP", null), 400, "BAD_REQUEST");
        expectError(checkout(cartId, "Petro", "not-an-email", "+380501234567", "STORE_PICKUP", null), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        expectError(checkout(cartId, " ", "petro@example.com", "+380501234567", "STORE_PICKUP", null), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"));
        expectError(checkout(cartId, "Petro", "petro@example.com", "+380501234567", "COURIER", null), 400, "BAD_REQUEST")
                .andExpect(jsonPath("$.message").value(containsString("Delivery address is required")));

        mvc.perform(get("/api/v1/carts/" + cartId)).andExpect(jsonPath("$.lines.length()").value(1));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(4));

        mvc.perform(checkout(cartId, "Petro", "petro@example.com", "+38 (050) 123-45-67", "STORE_PICKUP", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern("SS-\\d{6}-\\d{5,}")))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total.amount").value(799.9))
                .andExpect(jsonPath("$.paymentUrl").isEmpty());
    }

    @Test
    void catalogManagementRefusesInvalidProductsVariantsAndPromoCodes() throws Exception {
        String otherProductId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"Floor lamp " + suffix + "\",\"categoryId\":\"" + categoryId + "\"}")
                .header(AUTH, manager)).andExpect(status().isCreated()), "$.id");

        // requirement "Product variants": an article number belongs to one variant of the whole catalog
        expectError(addVariant(otherProductId, "lamp-" + suffix, "999.00"), 409, "BUSINESS_RULE_VIOLATED")
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
        expectError(addVariant(productId, "LAMP-B-" + suffix, "0.00"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
        expectError(addVariant(UUID.randomUUID().toString(), "LAMP-C-" + suffix, "10.00"), 404, "NOT_FOUND");
        expectError(json(post("/api/v1/management/products"), "{\"name\":\"\",\"categoryId\":\"" + categoryId + "\"}")
                .header(AUTH, manager), 400, "VALIDATION_FAILED");
        expectError(json(post("/api/v1/management/products"), "{\"name\":\"Lamp\",\"categoryId\":\"" + UUID.randomUUID() + "\"}")
                .header(AUTH, manager), 404, "NOT_FOUND");
        expectError(json(put("/api/v1/management/stock/" + variantId), "{\"onHand\":-1}").header(AUTH, manager),
                400, "VALIDATION_FAILED");

        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        String promoCode = "{\"code\":\"LAMP" + suffix + "\",\"discountPercent\":%d,\"validUntil\":\"%s\",\"usageLimit\":%d}";
        expectError(json(post("/api/v1/management/promo-codes"), promoCode.formatted(91, tomorrow, 1)).header(AUTH, manager),
                400, "VALIDATION_FAILED");
        expectError(json(post("/api/v1/management/promo-codes"), promoCode.formatted(10, tomorrow, 0)).header(AUTH, manager),
                400, "VALIDATION_FAILED");
        expectError(json(post("/api/v1/management/promo-codes"),
                promoCode.formatted(10, Instant.now().minus(1, ChronoUnit.DAYS).toString(), 1)).header(AUTH, manager),
                400, "VALIDATION_FAILED");
        mvc.perform(json(post("/api/v1/management/promo-codes"), promoCode.formatted(10, tomorrow, 1)).header(AUTH, manager))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LAMP" + suffix))
                .andExpect(jsonPath("$.discountPercent").value(10))
                .andExpect(jsonPath("$.usedCount").value(0));
        expectError(json(post("/api/v1/management/promo-codes"), promoCode.formatted(20, tomorrow, 5)).header(AUTH, manager),
                409, "BUSINESS_RULE_VIOLATED");
    }

    @Test
    void reviewRequestIsValidatedBeforeThePurchaseIsChecked() throws Exception {
        String url = "/api/v1/products/" + productId + "/reviews";

        expectError(json(post(url), "{\"rating\":6,\"text\":\"ok\"}").header(AUTH, customer), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
        expectError(json(post(url), "{\"rating\":5,\"text\":\"" + "a".repeat(2001) + "\"}").header(AUTH, customer),
                400, "VALIDATION_FAILED");
        expectError(json(post(url), "{\"rating\":5,\"text\":\"ok\"}"), 401, "UNAUTHORIZED");
        expectError(json(post(url), "{\"rating\":5,\"text\":\"ok\"}").header(AUTH, customer), 409, "BUSINESS_RULE_VIOLATED");
    }

    @Test
    void administrationRefusesInvalidSettingsPeriodsAndRoles() throws Exception {
        mvc.perform(get("/api/v1/admin/settings/delivery").header(AUTH, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierPrice.amount").isNumber())
                .andExpect(jsonPath("$.novaPoshtaPrice.currency").value("UAH"))
                .andExpect(jsonPath("$.freeShippingThreshold.amount").isNumber());
        expectError(json(put("/api/v1/admin/settings/delivery"),
                "{\"courierPrice\":-1,\"novaPoshtaPrice\":80,\"freeShippingThreshold\":2000}").header(AUTH, administrator),
                400, "VALIDATION_FAILED").andExpect(jsonPath("$.fieldErrors[0].field").value("courierPrice"));
        expectError(json(put("/api/v1/admin/settings/delivery"), "{\"courierPrice\":120}").header(AUTH, administrator),
                400, "VALIDATION_FAILED").andExpect(jsonPath("$.fieldErrors.length()").value(2));
        expectError(json(put("/api/v1/admin/settings/delivery"),
                "{\"courierPrice\":1,\"novaPoshtaPrice\":1,\"freeShippingThreshold\":1}").header(AUTH, manager), 403, "FORBIDDEN");

        expectError(get("/api/v1/admin/audit-log").param("limit", "0").header(AUTH, administrator), 400, "BAD_REQUEST");
        expectError(get("/api/v1/admin/audit-log").param("limit", "501").header(AUTH, administrator), 400, "BAD_REQUEST");
        mvc.perform(get("/api/v1/admin/audit-log").param("limit", "1").header(AUTH, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").isString())
                .andExpect(jsonPath("$[0].details").isString())
                .andExpect(jsonPath("$[0].occurredAt").isString());

        Instant now = Instant.now();
        expectError(get("/api/v1/admin/sales").param("from", now.toString()).param("to", now.minusSeconds(60).toString())
                .header(AUTH, administrator), 400, "BAD_REQUEST");
        expectError(get("/api/v1/admin/sales").param("from", "yesterday").param("to", now.toString())
                .header(AUTH, administrator), 400, "BAD_REQUEST");
        mvc.perform(get("/api/v1/admin/sales").param("from", now.minus(1, ChronoUnit.DAYS).toString())
                        .param("to", now.toString()).header(AUTH, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.ordersCount").isNumber())
                .andExpect(jsonPath("$.current.revenue.currency").value("UAH"))
                .andExpect(jsonPath("$.previous.ordersCount").isNumber());

        expectError(json(put("/api/v1/admin/users/" + UUID.randomUUID() + "/role"), "{\"role\":\"MANAGER\"}")
                .header(AUTH, administrator), 404, "NOT_FOUND");
        expectError(json(put("/api/v1/admin/users/" + UUID.randomUUID() + "/role"), "{\"role\":\"KING\"}")
                .header(AUTH, administrator), 400, "MALFORMED_REQUEST");
        expectError(json(put("/api/v1/admin/users/" + UUID.randomUUID() + "/role"), "{}")
                .header(AUTH, administrator), 400, "VALIDATION_FAILED");
    }

    /** NFR "Clear error messages" and "Protection from attacks": one format, no internals of the server. */
    @Test
    void everyErrorHasTheSameFieldsAndRevealsNothingAboutTheServer() throws Exception {
        expectError(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=a"), 415, "UNSUPPORTED_MEDIA_TYPE");
        expectError(json(post("/api/v1/auth/login"), "{\"email\":\"nobody" + suffix + "@example.com\",\"password\":\"x\"}"),
                401, "UNAUTHORIZED");
        expectError(json(post("/api/v1/auth/login"), "{\"email\":\"' OR 1=1 --\",\"password\":\"x\"}"), 401, "UNAUTHORIZED");
        expectError(get("/api/v1/products").param("query", "'; DROP TABLE catalog.products; --"), 200, null);
        expectError(get("/api/v1/orders/SS-000000-00000"), 404, "NOT_FOUND");
        expectError(post("/api/v1/orders/SS-000000-00000/cancel"), 401, "UNAUTHORIZED");
        expectError(get("/api/v1/me/orders").header(AUTH, "Bearer not.a.token"), 401, "UNAUTHORIZED");
    }

    /** Performs the request and checks the status and, for an error, the uniform error format. */
    private ResultActions expectError(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        ResultActions result = mvc.perform(request).andExpect(status().is(status));
        if (code == null) {
            return result;
        }
        return result
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.message").value(not(containsString("Exception"))))
                .andExpect(jsonPath("$.message").value(not(containsString("com.cycleofsoftwaredev"))))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    private ResultActions addVariant(String product, String sku, String price) throws Exception {
        return mvc.perform(json(post("/api/v1/management/products/" + product + "/variants"),
                "{\"sku\":\"" + sku + "\",\"name\":\"white\",\"price\":" + price + "}").header(AUTH, manager));
    }

    private ResultActions expectError(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    private MockHttpServletRequestBuilder checkout(String cartId, String fullName, String email, String phone,
                                                   String deliveryMethod, String address) {
        String deliveryAddress = address == null ? "null" : "\"" + address + "\"";
        return json(post("/api/v1/orders"), """
                {"cartId":"%s","fullName":"%s","email":"%s","phone":"%s","deliveryMethod":"%s","deliveryAddress":%s,
                 "paymentMethod":"CASH_ON_DELIVERY"}""".formatted(cartId, fullName, email, phone, deliveryMethod, deliveryAddress))
                .header("Idempotency-Key", UUID.randomUUID().toString());
    }

    /** Creates a user with the role and returns the value of the Authorization header with a fresh access token. */
    private String bearer(String name, Role role) {
        UserView user = registration.createUser(
                new RegisterUserCommand(name + suffix + "@example.com", "Contract " + name, "Contract12345"), role);
        return "Bearer " + tokens.issue(user).accessToken();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
