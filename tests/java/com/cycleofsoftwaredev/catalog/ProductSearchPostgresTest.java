package com.cycleofsoftwaredev.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductPage;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductSearch;
import com.cycleofsoftwaredev.catalog.domain.ProductSort;
import com.cycleofsoftwaredev.catalog.infrastructure.JdbcProductSearch;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.TestInfrastructure;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Catalog search executed by PostgreSQL (WHERE, ORDER BY, LIMIT). The same rules are checked for the
 * in-memory test double in ProductServiceTest; here they are checked for the real SQL.
 */
@Tag("component")
@Import(TestInfrastructure.class)
@SpringBootTest(properties = "shopsphere.demo-data.enabled=false")
class ProductSearchPostgresTest {

    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Autowired
    private ProductSearch productSearch;

    /** The database is shared with other tests, so every test works in its own category with its own word. */
    private String word;
    private Category home;
    private Category kitchen;
    private Category cookware;

    @BeforeEach
    void createCategories() {
        word = "w" + System.nanoTime();
        home = categories.createCategory("Home " + word, null);
        kitchen = categories.createCategory("Kitchen " + word, home.id());
        cookware = categories.createCategory("Cookware " + word, kitchen.id());
    }

    @Test
    void findsTextInNameAndDescriptionIgnoringCase() {
        product("Frying Pan " + word, "Cast iron", cookware, "700");
        product("Kettle " + word, "Stainless STEEL, 1.7 l", kitchen, "900");

        assertThat(productSearch).isInstanceOf(JdbcProductSearch.class);
        assertThat(names(word.toUpperCase(), null)).containsExactly("Frying Pan " + word, "Kettle " + word);
        assertThat(names("  FRYING pan " + word + " ", null)).containsExactly("Frying Pan " + word);
        assertThat(names("stainless steel", home.id())).containsExactly("Kettle " + word);
        assertThat(names("microwave " + word, null)).isEmpty();
    }

    @Test
    void categoryPageShowsProductsOfAllSubcategories() {
        Category toys = categories.createCategory("Toys " + word, null);
        product("Frying pan", "", cookware, "700");
        product("Kettle", "", kitchen, "900");
        product("Robot", "", toys, "300");

        assertThat(names(null, home.id())).containsExactly("Frying pan", "Kettle");
        assertThat(names(null, cookware.id())).containsExactly("Frying pan");
        assertThat(names(null, toys.id())).containsExactly("Robot");
    }

    @Test
    void hiddenProductsAreNotFound() {
        Product kettle = product("Kettle", "", kitchen, "900");
        product("Teapot", "", kitchen, "400");

        products.setVisible(kettle.id(), false);

        ProductPage page = products.searchPage(null, home.id(), ProductSort.NAME, 1, 24);
        assertThat(page.items()).extracting(Product::name).containsExactly("Teapot");
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    void sortsByNameIgnoringCaseAndByPriceOfTheCheapestVariant() {
        product("Kettle", "", kitchen, "900", "700");
        product("Blender", "", kitchen, "1500");
        product("cup", "", kitchen, "120");
        products.createProduct("Draft without variants", "", kitchen.id());

        assertThat(names(null, home.id(), ProductSort.NAME))
                .containsExactly("Blender", "cup", "Draft without variants", "Kettle");
        assertThat(names(null, home.id(), ProductSort.PRICE_ASC))
                .containsExactly("cup", "Kettle", "Blender", "Draft without variants");
        assertThat(names(null, home.id(), ProductSort.PRICE_DESC))
                .containsExactly("Blender", "Kettle", "cup", "Draft without variants");
    }

    @Test
    void returnsOnlyTheRequestedPageAndTheTotalNumber() {
        for (String name : List.of("A", "B", "C", "D", "E")) {
            product("Item " + name, "", kitchen, "100");
        }

        ProductPage second = products.searchPage(null, home.id(), ProductSort.NAME, 2, 2);
        ProductPage afterTheEnd = products.searchPage(null, home.id(), ProductSort.NAME, 4, 2);

        assertThat(second.items()).extracting(Product::name).containsExactly("Item C", "Item D");
        assertThat(second.items()).allSatisfy(item -> assertThat(item.variants()).hasSize(1));
        assertThat(second.totalItems()).isEqualTo(5);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(afterTheEnd.items()).isEmpty();
        assertThat(afterTheEnd.totalItems()).isEqualTo(5);
    }

    @Test
    void charactersWithASpecialMeaningInSqlAreSearchedLiterally() {
        product("100% cotton towel", "", kitchen, "250");
        product("1000 cotton balls", "it's a set_of 3", kitchen, "90");

        assertThat(names("100% cotton", home.id())).containsExactly("100% cotton towel");
        assertThat(names("set_of", home.id())).containsExactly("1000 cotton balls");
        assertThat(names("it's a", home.id())).containsExactly("1000 cotton balls");
        assertThat(names("'; DROP TABLE catalog.products; --", home.id())).isEmpty();
        assertThat(names("_", home.id())).containsExactly("1000 cotton balls");
    }

    private Product product(String name, String description, Category category, String... prices) {
        Product product = products.createProduct(name, description, category.id());
        for (String price : prices) {
            products.addVariant(product.id(), "SKU-" + UUID.randomUUID(), price, Money.of(price));
        }
        return product;
    }

    private List<String> names(String query, UUID categoryId) {
        return names(query, categoryId, ProductSort.NAME);
    }

    private List<String> names(String query, UUID categoryId, ProductSort sort) {
        return products.searchPage(query, categoryId, sort, 1, ProductPage.MAX_SIZE).items().stream()
                .map(Product::name)
                .toList();
    }
}
