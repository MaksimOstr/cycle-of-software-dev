package com.cycleofsoftwaredev.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
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
import com.cycleofsoftwaredev.support.TestInfrastructure;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * System testing (Laboratory Work 6): what the users of the store do from the first request to the last,
 * through the REST API only, on the whole application with the real PostgreSQL and Redis. Every scenario
 * follows one user story of the specification: a guest, a customer who forgot the password, the manager with
 * the administrator, and two customers who want the last items.
 */
@Tag("system")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
@AutoConfigureMockMvc
class UserScenariosSystemTest {

    private static final String AUTH = "Authorization";
    private static final String PASSWORD = "Customer12345";

    /** Replaces the email adapter: the test reads the letters as the user would read the mailbox. */
    @TestConfiguration
    static class Mailbox {

        @Bean
        @Primary
        ReceivedEmails receivedEmails() {
            return new ReceivedEmails();
        }
    }

    static class ReceivedEmails implements EmailSender {

        private final List<EmailMessage> received = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            received.add(message);
        }

        List<String> subjectsFor(String email) {
            return received.stream().filter(message -> message.to().equals(email)).map(EmailMessage::subject).toList();
        }

        String lastBodyFor(String email) {
            return received.stream().filter(message -> message.to().equals(email)).map(EmailMessage::body)
                    .reduce((first, second) -> second).orElseThrow();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RegistrationService registration;

    @Autowired
    private ReceivedEmails emails;

    private String suffix;
    private String adminToken;
    private String managerToken;
    private String categoryId;
    private String productId;
    private String variantId;

    /** The store before the scenario: an administrator, a manager and one product with 6 items in stock. */
    @BeforeEach
    void openTheStore() throws Exception {
        suffix = Long.toString(System.nanoTime());
        registration.createUser(new RegisterUserCommand("owner" + suffix + "@example.com", "Store Owner", "Owner12345"),
                Role.ADMINISTRATOR);
        adminToken = login("owner" + suffix + "@example.com", "Owner12345");
        String managerId = register("manager" + suffix + "@example.com", "Olena Manager", "Manager12345");
        mvc.perform(json(put("/api/v1/admin/users/" + managerId + "/role"), "{\"role\":\"MANAGER\"}").header(AUTH, adminToken))
                .andExpect(status().isOk());
        managerToken = login("manager" + suffix + "@example.com", "Manager12345");

        categoryId = read(mvc.perform(json(post("/api/v1/management/categories"), "{\"name\":\"Tea " + suffix + "\"}")
                .header(AUTH, managerToken)).andExpect(status().isCreated()), "$.id");
        productId = read(mvc.perform(json(post("/api/v1/management/products"),
                        "{\"name\":\"Teapot " + suffix + "\",\"description\":\"Glass, 1 l\",\"categoryId\":\"" + categoryId + "\"}")
                .header(AUTH, managerToken)).andExpect(status().isCreated()), "$.id");
        variantId = read(mvc.perform(json(post("/api/v1/management/products/" + productId + "/variants"),
                        "{\"sku\":\"TEA-" + suffix + "\",\"name\":\"clear\",\"price\":650.00}")
                .header(AUTH, managerToken)).andExpect(status().isCreated()), "$.variants[0].id");
        setStock(6);
    }

    /** User stories "browse the catalog", "checkout as a guest", "pay by card" and "track the order". */
    @Test
    void guestFindsAProductPaysByCardAndTracksTheOrderWithoutAnAccount() throws Exception {
        String guestEmail = "guest" + suffix + "@example.com";
        mvc.perform(get("/api/v1/products").param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(productId));
        mvc.perform(get("/api/v1/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].price.amount").value(650.0))
                .andExpect(jsonPath("$.variants[0].availableQuantity").value(6));

        String cartId = newCart(null);
        addToCart(cartId, 2).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal.amount").value(1300.0));
        String checkout = mvc.perform(order(cartId, guestEmail, "NOVA_POSHTA", "CARD_ONLINE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total.amount").value(1380.0)) // 2 x 650 + 80 Nova Poshta
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(checkout, "$.orderNumber");
        String paymentUrl = JsonPath.read(checkout, "$.paymentUrl");

        // the order number alone is not enough to see the order of somebody else
        mvc.perform(get("/api/v1/orders/" + orderNumber)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + orderNumber).param("email", "thief@example.com")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + orderNumber).param("email", guestEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.paymentDeadline").exists())
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        pay(paymentUrl);
        changeStatus(orderNumber, "{\"status\":\"PROCESSING\"}");
        changeStatus(orderNumber, "{\"status\":\"SHIPPED\",\"trackingNumber\":\"20450000000555\"}");

        mvc.perform(get("/api/v1/orders/" + orderNumber).param("email", guestEmail))
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("20450000000555"))
                .andExpect(jsonPath("$.history.length()").value(4));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(4));
        assertThat(emails.subjectsFor(guestEmail)).containsExactly("Order " + orderNumber + " created",
                "Order " + orderNumber + ": paid", "Order " + orderNumber + ": shipped");
        assertThat(emails.lastBodyFor(guestEmail)).contains("20450000000555");
        // a guest has no account, so the order can be cancelled only through the store
        mvc.perform(post("/api/v1/orders/" + orderNumber + "/cancel")).andExpect(status().isUnauthorized());
    }

    /** User stories "register", "log in and out", "recover the password" and "see the order history". */
    @Test
    void customerWhoForgotThePasswordGetsBackIntoTheAccountAndFindsTheOrders() throws Exception {
        String email = "petro" + suffix + "@example.com";
        String registered = mvc.perform(json(post("/api/v1/auth/register"),
                        "{\"email\":\"" + email + "\",\"fullName\":\"Petro Customer\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        assertThat(registered).doesNotContainIgnoringCase("password"); // NFR "Password storage"
        String tokens = mvc.perform(json(post("/api/v1/auth/login"), credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getContentAsString();
        assertThat(tokens).doesNotContainIgnoringCase("password");
        String accessToken = "Bearer " + JsonPath.<String>read(tokens, "$.accessToken");
        String refreshToken = JsonPath.read(tokens, "$.refreshToken");

        String cartId = newCart(accessToken);
        addToCart(cartId, 1).andExpect(status().isOk());
        String orderNumber = read(mvc.perform(order(cartId, email, "STORE_PICKUP", "CASH_ON_DELIVERY").header(AUTH, accessToken))
                .andExpect(status().isCreated()), "$.orderNumber");

        // logout: the refresh token is revoked
        mvc.perform(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isUnauthorized());

        // the password is forgotten: five wrong attempts lock the account even for the right password
        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(json(post("/api/v1/auth/login"), credentials(email, "Guess" + attempt + "2345")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        // the code from the email sets a new password and unlocks the account
        mvc.perform(json(post("/api/v1/auth/password-reset"), "{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());
        assertThat(emails.subjectsFor(email)).last().isEqualTo("ShopSphere password reset");
        String body = emails.lastBodyFor(email);
        String code = body.substring(body.indexOf("code: ") + 6, body.indexOf(" . It is valid"));
        mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), "{\"token\":\"" + code + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), "{\"token\":\"" + code + "\",\"newPassword\":\"Remember2026\"}"))
                .andExpect(status().isOk());

        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, PASSWORD))).andExpect(status().isUnauthorized());
        String newAccessToken = login(email, "Remember2026");
        mvc.perform(get("/api/v1/me/orders").header(AUTH, newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(orderNumber))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    /**
     * User stories "process an order", "return", "sales dashboard", "audit log" and "block a user":
     * the working day of the manager and the administrator.
     */
    @Test
    void managerDeliversTwoOrdersTakesOneBackAndTheAdministratorSeesItInTheSalesReport() throws Exception {
        String email = "buyer" + suffix + "@example.com";
        register(email, "Iryna Buyer", PASSWORD);
        String customerToken = login(email, PASSWORD);
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        String before = salesReport(from, to);

        String cardCart = newCart(customerToken);
        addToCart(cardCart, 1).andExpect(status().isOk());
        String cardCheckout = mvc.perform(order(cardCart, email, "STORE_PICKUP", "CARD_ONLINE").header(AUTH, customerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String cardOrder = JsonPath.read(cardCheckout, "$.orderNumber");
        pay(JsonPath.read(cardCheckout, "$.paymentUrl"));
        String cashCart = newCart(customerToken);
        addToCart(cashCart, 2).andExpect(status().isOk());
        String cashOrder = read(mvc.perform(order(cashCart, email, "STORE_PICKUP", "CASH_ON_DELIVERY").header(AUTH, customerToken))
                .andExpect(status().isCreated()), "$.orderNumber");

        mvc.perform(get("/api/v1/management/orders").param("search", email).param("status", "PAID").header(AUTH, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(cardOrder));
        for (String number : List.of(cardOrder, cashOrder)) {
            changeStatus(number, "{\"status\":\"PROCESSING\"}");
            changeStatus(number, "{\"status\":\"SHIPPED\",\"trackingNumber\":\"20450000000777\"}");
            changeStatus(number, "{\"status\":\"DELIVERED\"}");
        }
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(3));

        // the customer brings the first order back: the goods return to the stock, the revenue leaves the report
        mvc.perform(json(patch("/api/v1/management/orders/" + cardOrder + "/status"), "{\"status\":\"RETURNED\"}").header(AUTH, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(4));
        mvc.perform(json(patch("/api/v1/management/orders/" + cardOrder + "/status"), "{\"status\":\"PROCESSING\"}").header(AUTH, managerToken))
                .andExpect(status().isConflict());

        String after = salesReport(from, to);
        assertThat(ordersIn(after) - ordersIn(before)).isEqualTo(1);
        assertThat(revenueIn(after) - revenueIn(before)).isCloseTo(1300.0, within(0.001)); // only the cash order: 2 x 650
        mvc.perform(get("/api/v1/admin/sales").param("from", from.toString()).param("to", to.toString()).header(AUTH, managerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-log").param("limit", "100").header(AUTH, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'OrderStatusChanged')].details").value(
                        org.hamcrest.Matchers.hasItem(containsString(cardOrder))));

        // the administrator blocks the account: the access token that is still valid stops working at once
        String customerId = read(mvc.perform(get("/api/v1/admin/users").param("query", email).header(AUTH, adminToken))
                .andExpect(status().isOk()), "$[0].id");
        mvc.perform(post("/api/v1/admin/users/" + customerId + "/block").header(AUTH, adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/orders").header(AUTH, customerToken)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        mvc.perform(post("/api/v1/admin/users/" + customerId + "/unblock").header(AUTH, adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/orders").header(AUTH, customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /** User stories "hide a product" and "accurate stock": nobody buys what is hidden or already sold. */
    @Test
    void hiddenProductAndTheLastItemsCannotBeBoughtTwice() throws Exception {
        setStock(2);
        String slowCart = newCart(null);
        addToCart(slowCart, 2).andExpect(status().isOk());
        String fastCart = newCart(null);
        addToCart(fastCart, 2).andExpect(status().isOk());

        // the manager hides the product: it disappears from the catalog and from the carts
        mvc.perform(json(patch("/api/v1/management/products/" + productId + "/visibility"), "{\"visible\":false}").header(AUTH, managerToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products").param("query", "teapot " + suffix)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/carts/" + slowCart)).andExpect(jsonPath("$.lines.length()").value(0));
        mvc.perform(order(slowCart, "slow" + suffix + "@example.com", "STORE_PICKUP", "CASH_ON_DELIVERY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The cart is empty"));
        mvc.perform(json(patch("/api/v1/management/products/" + productId + "/visibility"), "{\"visible\":true}").header(AUTH, managerToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/carts/" + slowCart)).andExpect(jsonPath("$.lines[0].quantity").value(2));

        // two customers want the last two items: the second one is told that nothing is left
        mvc.perform(order(fastCart, "fast" + suffix + "@example.com", "STORE_PICKUP", "CASH_ON_DELIVERY"))
                .andExpect(status().isCreated());
        mvc.perform(order(slowCart, "slow" + suffix + "@example.com", "STORE_PICKUP", "CASH_ON_DELIVERY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATED"))
                .andExpect(jsonPath("$.message").value(containsString("Only 0 item(s) left")));
        mvc.perform(get("/api/v1/carts/" + slowCart)).andExpect(jsonPath("$.lines[0].quantity").value(2));

        // new goods arrive: the order of the second customer is accepted
        setStock(4);
        mvc.perform(order(slowCart, "slow" + suffix + "@example.com", "STORE_PICKUP", "CASH_ON_DELIVERY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total.amount").value(1300.0));
        mvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.variants[0].availableQuantity").value(0));
    }

    private void setStock(int onHand) throws Exception {
        mvc.perform(json(put("/api/v1/management/stock/" + variantId), "{\"onHand\":" + onHand + "}").header(AUTH, managerToken))
                .andExpect(status().isOk());
    }

    private String newCart(String token) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/carts");
        if (token != null) {
            request.header(AUTH, token);
        }
        return read(mvc.perform(request).andExpect(status().isCreated()), "$.cartId");
    }

    private ResultActions addToCart(String cartId, int quantity) throws Exception {
        return mvc.perform(json(post("/api/v1/carts/" + cartId + "/items"),
                "{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}"));
    }

    private MockHttpServletRequestBuilder order(String cartId, String email, String deliveryMethod, String paymentMethod) {
        return json(post("/api/v1/orders"), """
                {"cartId":"%s","fullName":"Store Customer","email":"%s","phone":"+38 (050) 123-45-67",
                 "deliveryMethod":"%s","deliveryAddress":"Lviv, branch 7","paymentMethod":"%s"}
                """.formatted(cartId, email, deliveryMethod, paymentMethod))
                .header("Idempotency-Key", UUID.randomUUID().toString());
    }

    /** What the payment provider does after the customer has entered the card data on its page. */
    private void pay(String paymentUrl) throws Exception {
        String sessionId = paymentUrl.substring(paymentUrl.lastIndexOf('/') + 1);
        mvc.perform(json(post("/api/v1/payments/webhook"), "{\"id\":\"evt_" + UUID.randomUUID()
                        + "\",\"type\":\"checkout.session.completed\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk());
    }

    private void changeStatus(String orderNumber, String body) throws Exception {
        mvc.perform(json(patch("/api/v1/management/orders/" + orderNumber + "/status"), body).header(AUTH, managerToken))
                .andExpect(status().isOk());
    }

    private String salesReport(Instant from, Instant to) throws Exception {
        return mvc.perform(get("/api/v1/admin/sales").param("from", from.toString()).param("to", to.toString()).header(AUTH, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static int ordersIn(String report) {
        return JsonPath.<Number>read(report, "$.current.ordersCount").intValue();
    }

    private static double revenueIn(String report) {
        return JsonPath.<Number>read(report, "$.current.revenue.amount").doubleValue();
    }

    private String register(String email, String fullName, String password) throws Exception {
        return read(mvc.perform(json(post("/api/v1/auth/register"),
                        "{\"email\":\"" + email + "\",\"fullName\":\"" + fullName + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated()), "$.id");
    }

    /** Signs in through the API and returns the value of the Authorization header. */
    private String login(String email, String password) throws Exception {
        return "Bearer " + read(mvc.perform(json(post("/api/v1/auth/login"), credentials(email, password)))
                .andExpect(status().isOk()), "$.accessToken");
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
