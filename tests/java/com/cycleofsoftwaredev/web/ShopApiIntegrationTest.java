package com.cycleofsoftwaredev.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end scenario through the REST API: catalog, cart, checkout, payment webhook, order management. */
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
@AutoConfigureMockMvc
class ShopApiIntegrationTest {

    private static final String USER = CurrentUserResolver.HEADER;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registration;

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

        String cartId = read(mvc.perform(post("/api/v1/carts").header(USER, customer.id()))
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
                        .header("Idempotency-Key", idempotencyKey).header(USER, customer.id()))
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
        mvc.perform(get("/api/v1/orders/" + orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // a customer may not manage orders, a manager may
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"), "{\"status\":\"PROCESSING\"}")
                        .header(USER, customer.id()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"), "{\"status\":\"PROCESSING\"}")
                        .header(USER, manager.id()))
                .andExpect(status().isOk());
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"),
                        "{\"status\":\"SHIPPED\",\"trackingNumber\":\"20450000000001\"}").header(USER, manager.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("20450000000001"));

        mvc.perform(get("/api/v1/me/orders").header(USER, customer.id()))
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
    void registersAndLogsIn() throws Exception {
        String email = "new" + System.nanoTime() + "@example.com";
        mvc.perform(json(post("/api/v1/auth/register"),
                        "{\"email\":\"" + email + "\",\"fullName\":\"New Customer\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"wrong1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String createProductInStock(String sku, String price, int quantity) throws Exception {
        String categoryId = read(mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Hoodies\"}")
                .header(USER, manager.id())).andExpect(status().isCreated()), "$.id");
        String productId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"Hoodie\",\"description\":\"Cotton\",\"categoryId\":\"" + categoryId + "\"}")
                .header(USER, manager.id())).andExpect(status().isCreated()), "$.id");
        String variantId = read(mvc.perform(json(post("/api/v1/management/products/" + productId + "/variants"),
                        "{\"sku\":\"" + sku + "\",\"name\":\"M\",\"price\":" + price + "}")
                .header(USER, manager.id())).andExpect(status().isCreated()), "$.variants[0].id");
        mvc.perform(json(put("/api/v1/management/stock/" + variantId), "{\"onHand\":" + quantity + "}")
                        .header(USER, manager.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(quantity));
        productIds.put(variantId, productId);
        return variantId;
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
