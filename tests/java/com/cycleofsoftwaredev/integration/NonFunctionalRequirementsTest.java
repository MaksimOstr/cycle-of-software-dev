package com.cycleofsoftwaredev.integration;

import com.cycleofsoftwaredev.support.TestInfrastructure;
import org.springframework.context.annotation.Import;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checks of the non-functional requirements that can be measured automatically on the integrated system.
 * They run on the real PostgreSQL started by Testcontainers.
 */
@Tag("system")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
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

    @Autowired
    private JdbcTemplate jdbc;

    private UUID performanceCategoryId;

    /** The database is shared with the other test classes, so the large catalog is removed after the test. */
    @AfterEach
    void removeLargeCatalog() {
        if (performanceCategoryId != null) {
            jdbc.update("""
                    DELETE FROM inventory.stock_items WHERE variant_id IN (
                        SELECT v.id FROM catalog.product_variants v
                        JOIN catalog.products p ON p.id = v.product_id WHERE p.category_id = ?)""", performanceCategoryId);
            jdbc.update("DELETE FROM catalog.products WHERE category_id = ?", performanceCategoryId);
        }
    }

    /** NFR "Server response time": 95% of catalog, search and cart responses are returned within 500 ms. */
    @Test
    void catalogSearchAndCartRespondWithinHalfASecond() throws Exception {
        Category category = categories.createCategory("Performance " + System.nanoTime(), null);
        performanceCategoryId = category.id();
        UUID variantId = null;
        List<Object[]> productRows = new ArrayList<>();
        List<Object[]> variantRows = new ArrayList<>();
        List<Object[]> stockRows = new ArrayList<>();
        for (int i = 0; i < CATALOG_SIZE; i++) {
            UUID productId = UUID.randomUUID();
            variantId = UUID.randomUUID();
            productRows.add(new Object[] {productId, "Item " + i, "Description of item " + i, category.id()});
            variantRows.add(new Object[] {productId, variantId, "PERF-" + variantId});
            stockRows.add(new Object[] {variantId});
        }
        jdbc.batchUpdate("INSERT INTO catalog.products (id, name, description, category_id, active) "
                + "VALUES (?, ?, ?, ?, true)", productRows);
        jdbc.batchUpdate("INSERT INTO catalog.product_variants (product_id, position, id, sku, name, price) "
                + "VALUES (?, 0, ?, ?, 'default', 100.00)", variantRows);
        jdbc.batchUpdate("INSERT INTO inventory.stock_items (variant_id, on_hand, reserved, low_stock_threshold) "
                + "VALUES (?, 1000, 0, 5)", stockRows);
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
