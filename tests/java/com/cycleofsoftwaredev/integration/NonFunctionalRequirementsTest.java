package com.cycleofsoftwaredev.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checks of the non-functional requirements that can be measured automatically on the integrated system.
 * The test has its own application context (its own name), so the large catalog does not get into other tests.
 */
@SpringBootTest(properties = {"shopsphere.demo-data.enabled=false", "spring.application.name=shopsphere-nfr-test"})
@AutoConfigureMockMvc
class NonFunctionalRequirementsTest {

    private static final int CATALOG_SIZE = 5_000;
    private static final Duration RESPONSE_TIME_LIMIT = Duration.ofMillis(500);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Autowired
    private InventoryService inventory;

    /** NFR "Server response time": 95% of catalog, search and cart responses are returned within 500 ms. */
    @Test
    void catalogSearchAndCartRespondWithinHalfASecond() throws Exception {
        Category category = categories.createCategory("Performance " + System.nanoTime(), null);
        UUID variantId = null;
        for (int i = 0; i < CATALOG_SIZE; i++) {
            Product product = products.createProduct("Item " + i, "Description of item " + i, category.id());
            variantId = products.addVariant(product.id(), "PERF-" + System.nanoTime() + "-" + i, "default", Money.of("100")).id();
            inventory.setStock(variantId, 1_000);
        }
        String cartId = JsonPath.read(mvc.perform(post("/api/v1/carts")).andReturn().getResponse().getContentAsString(), "$.cartId");
        MockHttpServletRequestBuilder addToCart = post("/api/v1/carts/" + cartId + "/items")
                .contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}");

        for (int i = 0; i < 5; i++) {
            // the first requests initialize the web layer and are not typical
            timeOf(get("/api/v1/products").param("query", "item"));
        }

        List<Duration> times = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            times.add(timeOf(get("/api/v1/products").param("categoryId", category.id().toString()).param("page", "3")));
            times.add(timeOf(get("/api/v1/products").param("query", "item 49" + i).param("sort", "PRICE_ASC")));
            times.add(timeOf(get("/api/v1/categories")));
            times.add(timeOf(addToCart));
        }

        Collections.sort(times);
        Duration percentile95 = times.get((int) Math.ceil(times.size() * 0.95) - 1);
        assertThat(percentile95).isLessThan(RESPONSE_TIME_LIMIT);
    }

    /** Goal "Provide accurate stock information": no orders for out-of-stock products, even at the same moment. */
    @Test
    void customersWhoOrderAtTheSameTimeCannotBuyMoreThanIsInStock() throws Exception {
        Category category = categories.createCategory("Concurrency " + System.nanoTime(), null);
        Product product = products.createProduct("Last items", "", category.id());
        UUID variantId = products.addVariant(product.id(), "LAST-" + System.nanoTime(), "default", Money.of("100")).id();
        inventory.setStock(variantId, 5);

        List<Callable<Integer>> customers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String cartId = JsonPath.read(mvc.perform(post("/api/v1/carts")).andReturn().getResponse().getContentAsString(), "$.cartId");
            mvc.perform(post("/api/v1/carts/" + cartId + "/items").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}")).andExpect(status().isOk());
            String order = """
                    {"cartId":"%s","fullName":"Guest","email":"guest%d@example.com","phone":"+380501234567",
                     "deliveryMethod":"STORE_PICKUP","paymentMethod":"CASH_ON_DELIVERY"}""".formatted(cartId, i);
            customers.add(() -> mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(order)
                    .header("Idempotency-Key", UUID.randomUUID().toString())).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(customers.size())) {
            for (Future<Integer> result : executor.invokeAll(customers)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(15);
        assertThat(inventory.availableQuantity(variantId)).isZero();
    }

    private Duration timeOf(MockHttpServletRequestBuilder request) throws Exception {
        long start = System.nanoTime();
        mvc.perform(request).andExpect(status().isOk());
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
