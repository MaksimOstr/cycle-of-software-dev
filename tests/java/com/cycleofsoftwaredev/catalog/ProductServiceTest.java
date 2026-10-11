package com.cycleofsoftwaredev.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductPage;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.application.ProductSort;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.catalog.infrastructure.InMemoryCategoryRepository;
import com.cycleofsoftwaredev.catalog.infrastructure.InMemoryProductRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final CategoryService categories = new CategoryService(new InMemoryCategoryRepository(), productRepository);
    private final ProductService products = new ProductService(productRepository, categories);

    @Test
    void createsProductWithVariantsAndExposesThemThroughCatalogApi() {
        Category phones = categories.createCategory("Smartphones", null);
        Product phone = products.createProduct("Galaxy S30", "Flagship phone", phones.id());
        ProductVariant variant = products.addVariant(phone.id(), "GS30-128", "128 GB", Money.of("32999"));

        VariantView view = products.findVariant(variant.id()).orElseThrow();

        assertThat(view.productId()).isEqualTo(phone.id());
        assertThat(view.productName()).isEqualTo("Galaxy S30");
        assertThat(view.price()).isEqualTo(Money.of("32999.00"));
        assertThat(view.available()).isTrue();
    }

    @Test
    void searchesByTextAndCategory() {
        Category phones = categories.createCategory("Smartphones", null);
        Category clothing = categories.createCategory("Clothing", null);
        products.createProduct("Galaxy S30", "Flagship phone", phones.id());
        products.createProduct("Basic Hoodie", "Cotton hoodie", clothing.id());

        assertThat(products.search("galaxy", null)).extracting(Product::name).containsExactly("Galaxy S30");
        assertThat(products.search(null, clothing.id())).extracting(Product::name).containsExactly("Basic Hoodie");
        assertThat(products.search("", null)).hasSize(2);
    }

    @Test
    void rejectsDuplicateSkuAndUnknownCategory() {
        Category phones = categories.createCategory("Smartphones", null);
        Product phone = products.createProduct("Galaxy S30", null, phones.id());
        products.addVariant(phone.id(), "GS30-128", "128 GB", Money.of("32999"));

        assertThatThrownBy(() -> products.addVariant(phone.id(), "gs30-128", "copy", Money.of("1")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> products.createProduct("Phone", null, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void categoryPageShowsProductsOfAllSubcategories() {
        Category home = categories.createCategory("Home", null);
        Category kitchen = categories.createCategory("Kitchen", home.id());
        Category cookware = categories.createCategory("Cookware", kitchen.id());
        Category toys = categories.createCategory("Toys", null);
        products.createProduct("Frying pan", "", cookware.id());
        products.createProduct("Kettle", "", kitchen.id());
        products.createProduct("Robot", "", toys.id());

        assertThat(products.search(null, home.id())).extracting(Product::name).containsExactly("Frying pan", "Kettle");
        assertThat(products.search(null, cookware.id())).extracting(Product::name).containsExactly("Frying pan");
    }

    @Test
    void categoryTreeHasAtMostThreeLevels() {
        Category home = categories.createCategory("Home", null);
        Category kitchen = categories.createCategory("Kitchen", home.id());
        Category cookware = categories.createCategory("Cookware", kitchen.id());

        assertThatThrownBy(() -> categories.createCategory("Pans", cookware.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 3 levels");
    }

    @Test
    void sortsByPriceOfTheCheapestVariantAndSplitsIntoPages() {
        Category category = categories.createCategory("Kitchen", null);
        product("Kettle", category, "900", "700");
        product("Blender", category, "1500");
        product("Cup", category, "120");
        products.createProduct("Draft without variants", "", category.id());

        assertThat(products.search(null, null, ProductSort.PRICE_ASC)).extracting(Product::name)
                .containsExactly("Cup", "Kettle", "Blender", "Draft without variants");
        assertThat(products.search(null, null, ProductSort.PRICE_DESC)).extracting(Product::name)
                .containsExactly("Blender", "Kettle", "Cup", "Draft without variants");

        ProductPage secondPage = products.searchPage(null, null, ProductSort.NAME, 2, 3);
        assertThat(secondPage.items()).extracting(Product::name).containsExactly("Kettle");
        assertThat(secondPage.totalItems()).isEqualTo(4);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThatThrownBy(() -> products.searchPage(null, null, ProductSort.NAME, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> products.searchPage(null, null, ProductSort.NAME, 1, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editsProduct() {
        Category kitchen = categories.createCategory("Kitchen", null);
        Category toys = categories.createCategory("Toys", null);
        Product product = products.createProduct("Ketle", "Typo", kitchen.id());

        products.updateProduct(product.id(), "Kettle", "1.7 l", toys.id());

        Product updated = products.getProduct(product.id());
        assertThat(updated.name()).isEqualTo("Kettle");
        assertThat(updated.description()).isEqualTo("1.7 l");
        assertThat(updated.categoryId()).isEqualTo(toys.id());
        assertThatThrownBy(() -> products.updateProduct(product.id(), "Kettle", "", UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hiddenProductIsNotVisibleToCustomers() {
        Category kitchen = categories.createCategory("Kitchen", null);
        Product product = products.createProduct("Kettle", "", kitchen.id());
        ProductVariant variant = products.addVariant(product.id(), "KET-1", "white", Money.of("900"));

        products.setVisible(product.id(), false);

        assertThat(products.search(null, null)).isEmpty();
        assertThat(products.allProducts()).extracting(Product::name).containsExactly("Kettle");
        assertThat(products.findVariant(variant.id()).orElseThrow().available()).isFalse();
        assertThatThrownBy(() -> products.getVisibleProduct(product.id())).isInstanceOf(NotFoundException.class);

        products.setVisible(product.id(), true);
        assertThat(products.getVisibleProduct(product.id()).name()).isEqualTo("Kettle");
    }

    @Test
    void deletesProduct() {
        Category kitchen = categories.createCategory("Kitchen", null);
        Product product = products.createProduct("Kettle", "", kitchen.id());
        ProductVariant variant = products.addVariant(product.id(), "KET-1", "white", Money.of("900"));

        products.deleteProduct(product.id());

        assertThat(products.allProducts()).isEmpty();
        assertThat(products.findVariant(variant.id())).isEmpty();
        assertThatThrownBy(() -> products.deleteProduct(product.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void renamesCategoryAndDeletesOnlyEmptyOne() {
        Category home = categories.createCategory("Home", null);
        Category kitchen = categories.createCategory("Kichen", home.id());
        Product kettle = products.createProduct("Kettle", "", kitchen.id());

        assertThat(categories.renameCategory(kitchen.id(), "Kitchen")).isEqualTo(new Category(kitchen.id(), "Kitchen", home.id()));
        assertThatThrownBy(() -> categories.deleteCategory(home.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("subcategories");
        assertThatThrownBy(() -> categories.deleteCategory(kitchen.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("contains products");

        products.deleteProduct(kettle.id());
        categories.deleteCategory(kitchen.id());
        categories.deleteCategory(home.id());

        assertThat(categories.listCategories()).isEmpty();
    }

    private void product(String name, Category category, String... prices) {
        Product product = products.createProduct(name, "", category.id());
        for (String price : prices) {
            products.addVariant(product.id(), name + "-" + price, price, Money.of(price));
        }
    }
}
