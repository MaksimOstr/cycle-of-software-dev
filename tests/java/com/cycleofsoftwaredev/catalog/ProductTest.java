package com.cycleofsoftwaredev.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules of a product, its variants and a category, without the services and the storage. */
class ProductTest {

    private final UUID categoryId = UUID.randomUUID();
    private final Product hoodie = new Product(UUID.randomUUID(), "  Basic Hoodie ", null, categoryId);

    @Test
    void nameIsTrimmedAndMissingDescriptionIsEmpty() {
        assertThat(hoodie.name()).isEqualTo("Basic Hoodie");
        assertThat(hoodie.description()).isEmpty();
        assertThat(hoodie.isActive()).isTrue();
    }

    @Test
    void nameAndCategoryAreRequired() {
        assertThatThrownBy(() -> new Product(UUID.randomUUID(), " ", "", categoryId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hoodie.update(null, "", categoryId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hoodie.update("Hoodie", "", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void priceOfAProductIsThePriceOfItsCheapestVariant() {
        assertThat(hoodie.lowestPrice()).isEmpty();

        hoodie.addVariant("HOOD-M", "M", Money.of("1299.00"));
        hoodie.addVariant("HOOD-S", "S", Money.of("1199.00"));
        hoodie.addVariant("HOOD-XL", "XL", Money.of("1399.00"));

        assertThat(hoodie.lowestPrice()).contains(Money.of("1199.00"));
        assertThat(hoodie.variants()).extracting(ProductVariant::sku).containsExactly("HOOD-M", "HOOD-S", "HOOD-XL");
    }

    @Test
    void skuIsUniqueInsideAProductWhateverTheLetterCase() {
        hoodie.addVariant("HOOD-M", "M", Money.of("1299.00"));

        assertThatThrownBy(() -> hoodie.addVariant("hood-m", "M again", Money.of("1.00")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(hoodie.variants()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "0.004"})
    void variantPriceMustBePositive(String price) {
        assertThatThrownBy(() -> hoodie.addVariant("HOOD-M", "M", Money.of(price)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(hoodie.variants()).isEmpty();
    }

    @Test
    void variantNeedsASku() {
        assertThatThrownBy(() -> hoodie.addVariant(" ", "M", Money.of("10"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hoodie.addVariant(null, "M", Money.of("10"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductVariant(UUID.randomUUID(), "SKU", "M", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void variantIsShownToOtherModulesWithTheNameOfItsProduct() {
        ProductVariant variant = hoodie.addVariant("HOOD-M", "M", Money.of("1299.00"));

        VariantView view = hoodie.toVariantView(hoodie.findVariant(variant.id()).orElseThrow());

        assertThat(view).isEqualTo(new VariantView(variant.id(), hoodie.id(), "HOOD-M", "Basic Hoodie", "M",
                Money.of("1299.00"), true));
        assertThat(hoodie.findVariant(UUID.randomUUID())).isEmpty();
    }

    @Test
    void hiddenProductMarksItsVariantsAsUnavailable() {
        ProductVariant variant = hoodie.addVariant("HOOD-M", "M", Money.of("1299.00"));

        hoodie.deactivate();
        assertThat(hoodie.toVariantView(variant).available()).isFalse();

        hoodie.activate();
        assertThat(hoodie.toVariantView(variant).available()).isTrue();
    }

    @Test
    void textIsSearchedInNameAndDescriptionWithoutLetterCase() {
        Product kettle = new Product(UUID.randomUUID(), "Kettle", "Electric, 1.7 litres", categoryId);

        assertThat(kettle.matches("KETT")).isTrue();
        assertThat(kettle.matches(" electric ")).isTrue();
        assertThat(kettle.matches("")).isTrue();
        assertThat(kettle.matches(null)).isTrue();
        assertThat(kettle.matches("hoodie")).isFalse();
    }

    @Test
    void categoryHasANameAndMayHaveAParent() {
        Category clothing = new Category(UUID.randomUUID(), " Clothing ", null);
        Category hoodies = new Category(UUID.randomUUID(), "Hoodies", clothing.id());

        assertThat(clothing.name()).isEqualTo("Clothing");
        assertThat(clothing.isRoot()).isTrue();
        assertThat(hoodies.isRoot()).isFalse();
        assertThat(hoodies.parentId()).isEqualTo(clothing.id());
        assertThatThrownBy(() -> new Category(UUID.randomUUID(), "", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Category(UUID.randomUUID(), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoriesAreEqualWhenAllTheirDataIsEqual() {
        UUID id = UUID.randomUUID();
        Category clothing = new Category(id, "Clothing", null);

        assertThat(clothing).isEqualTo(new Category(id, "Clothing", null)).hasSameHashCodeAs(new Category(id, "Clothing", null));
        assertThat(clothing).isNotEqualTo(new Category(id, "Clothes", null));
        assertThat(clothing).isNotEqualTo(new Category(id, "Clothing", UUID.randomUUID()));
        assertThat(clothing).isNotEqualTo(new Category(UUID.randomUUID(), "Clothing", null));
        assertThat(clothing.toString()).contains("Clothing");
    }
}
