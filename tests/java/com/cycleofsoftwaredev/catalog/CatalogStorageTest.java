package com.cycleofsoftwaredev.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Catalog module with its tables in the real PostgreSQL started by Testcontainers. */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class CatalogStorageTest {

    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** The database is shared with the other test classes, so every test uses its own names. */
    private String suffix;
    private Category category;

    @BeforeEach
    void createCategory() {
        suffix = Long.toString(System.nanoTime());
        category = categories.createCategory("Clothing " + suffix, null);
    }

    @Test
    void productIsReadBackWithVariantsInTheOrderTheyWereAdded() {
        Product hoodie = products.createProduct("Hoodie " + suffix, "Cotton, unisex", category.id());
        ProductVariant medium = products.addVariant(hoodie.id(), "HOOD-M-" + suffix, "M", Money.of("1299.00"));
        ProductVariant large = products.addVariant(hoodie.id(), "HOOD-L-" + suffix, "L", Money.of("1349.50"));
        ProductVariant small = products.addVariant(hoodie.id(), "HOOD-S-" + suffix, "S", Money.of("1199.99"));

        Product loaded = productRepository.findById(hoodie.id()).orElseThrow();

        assertThat(loaded.name()).isEqualTo("Hoodie " + suffix);
        assertThat(loaded.description()).isEqualTo("Cotton, unisex");
        assertThat(loaded.categoryId()).isEqualTo(category.id());
        assertThat(loaded.variants()).containsExactly(medium, large, small);
        assertThat(loaded.lowestPrice()).contains(Money.of("1199.99"));
        assertThat(productRepository.findByVariantId(large.id())).map(Product::id).contains(hoodie.id());
        assertThat(productRepository.findByVariantId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void articleNumberCannotBeUsedByTwoProducts() {
        Product hoodie = products.createProduct("Hoodie " + suffix, "", category.id());
        Product jacket = products.createProduct("Jacket " + suffix, "", category.id());
        products.addVariant(hoodie.id(), "Wear-" + suffix, "M", Money.of("1299.00"));

        assertThat(productRepository.existsByVariantSku("WEAR-" + suffix)).isTrue();
        assertThat(productRepository.existsByVariantSku("WEAR-" + suffix + "-2")).isFalse();
        assertThatThrownBy(() -> products.addVariant(jacket.id(), "wear-" + suffix, "M", Money.of("2500.00")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(products.getProduct(jacket.id()).variants()).isEmpty();
    }

    /** Two managers add the same article number at the same moment: the check of the service is not enough. */
    @Test
    void databaseItselfRejectsADuplicateArticleNumber() {
        Product hoodie = products.createProduct("Hoodie " + suffix, "", category.id());
        Product jacket = products.createProduct("Jacket " + suffix, "", category.id());
        products.addVariant(hoodie.id(), "DUP-" + suffix, "M", Money.of("1299.00"));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO catalog.product_variants (product_id, position, id, sku, name, price)
                VALUES (?, 0, ?, ?, 'M', 100.00)""", jacket.id(), UUID.randomUUID(), "dup-" + suffix))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAPriceThatIsNotPositive() {
        Product hoodie = products.createProduct("Hoodie " + suffix, "", category.id());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO catalog.product_variants (product_id, position, id, sku, name, price)
                VALUES (?, 0, ?, ?, 'M', 0.00)""", hoodie.id(), UUID.randomUUID(), "FREE-" + suffix))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletedProductLeavesNoVariantsBehind() {
        Product hoodie = products.createProduct("Hoodie " + suffix, "", category.id());
        products.addVariant(hoodie.id(), "DEL-" + suffix, "M", Money.of("1299.00"));

        products.deleteProduct(hoodie.id());

        assertThat(productRepository.findById(hoodie.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog.product_variants WHERE product_id = ?",
                Integer.class, hoodie.id())).isZero();
        assertThat(productRepository.existsByVariantSku("DEL-" + suffix)).isFalse();
    }

    @Test
    void categoryWithProductsOrSubcategoriesIsKept() {
        Category hoodies = categories.createCategory("Hoodies " + suffix, category.id());
        Product hoodie = products.createProduct("Hoodie " + suffix, "", hoodies.id());

        assertThatThrownBy(() -> categories.deleteCategory(category.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> categories.deleteCategory(hoodies.id())).isInstanceOf(BusinessRuleException.class);

        products.deleteProduct(hoodie.id());
        categories.deleteCategory(hoodies.id());
        categories.deleteCategory(category.id());

        assertThat(categories.listCategories()).extracting(Category::id).doesNotContain(category.id(), hoodies.id());
    }

    @Test
    void renamedCategoryKeepsItsPlaceInTheTree() {
        Category hoodies = categories.createCategory("Hoodies " + suffix, category.id());

        categories.renameCategory(hoodies.id(), "Sweatshirts " + suffix);

        assertThat(categories.getCategory(hoodies.id()))
                .isEqualTo(new Category(hoodies.id(), "Sweatshirts " + suffix, category.id()));
        assertThat(categories.branchIds(category.id())).containsExactlyInAnyOrder(category.id(), hoodies.id());
    }
}
