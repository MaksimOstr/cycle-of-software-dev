package com.cycleofsoftwaredev.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
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

    private final CategoryService categories = new CategoryService(new InMemoryCategoryRepository());
    private final ProductService products = new ProductService(new InMemoryProductRepository(), categories);

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
}
