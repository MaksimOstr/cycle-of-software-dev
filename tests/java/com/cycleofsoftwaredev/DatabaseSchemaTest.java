package com.cycleofsoftwaredev;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The Liquibase changelog applied to an empty PostgreSQL started by Testcontainers. The application starts
 * with {@code ddl-auto=validate}, so a loaded context already proves that the tables match the entities.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class DatabaseSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** Laboratory Work 2, section 2.4: every module keeps its tables in its own schema. */
    @Test
    void everyModuleWithDataHasItsOwnSchema() {
        List<String> schemas = jdbc.queryForList("SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).contains("catalog", "inventory", "cart", "ordering", "payment", "identity", "reviews",
                "administration");
        assertThat(jdbc.queryForList("""
                SELECT table_schema || '.' || table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name NOT LIKE 'databasechangelog%'""", String.class)).isEmpty();
    }

    @Test
    void everyChangeOfTheChangelogWasApplied() throws IOException {
        int written = 0;
        for (Resource file : new PathMatchingResourcePatternResolver().getResources("classpath:db/changelog/changes/*.sql")) {
            written += (int) file.getContentAsString(StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("--changeset")).count();
        }

        Integer applied = jdbc.queryForObject("SELECT count(*) FROM databasechangelog WHERE exectype = 'EXECUTED'", Integer.class);

        assertThat(written).isGreaterThanOrEqualTo(10);
        assertThat(applied).isEqualTo(written);
    }

    @Test
    void storeHasExactlyOneRowOfSettings() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administration.store_settings", Integer.class)).isEqualTo(1);
    }

    @Test
    void lookupsOfTheServicesHaveIndexes() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname NOT IN ('pg_catalog', 'public')", String.class);

        assertThat(indexes).contains("products_category_idx", "product_variants_sku_idx", "stock_reservations_order_idx",
                "orders_customer_idx", "orders_status_idx", "orders_created_idx", "carts_updated_idx", "audit_log_occurred_idx");
    }
}
