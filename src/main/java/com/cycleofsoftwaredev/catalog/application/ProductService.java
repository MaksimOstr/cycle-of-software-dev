package com.cycleofsoftwaredev.catalog.application;

import com.cycleofsoftwaredev.catalog.api.CatalogApi;
import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use cases "Product management", "Product variants", "Product listing" and "Product search". */
@Service
public class ProductService implements CatalogApi {

    private final ProductRepository products;
    private final CategoryService categories;

    public ProductService(ProductRepository products, CategoryService categories) {
        this.products = products;
        this.categories = categories;
    }

    public Product createProduct(String name, String description, UUID categoryId) {
        categories.getCategory(categoryId);
        return products.save(new Product(UUID.randomUUID(), name, description, categoryId));
    }

    public ProductVariant addVariant(UUID productId, String sku, String variantName, Money price) {
        Product product = getProduct(productId);
        ProductVariant variant = product.addVariant(sku, variantName, price);
        products.save(product);
        return variant;
    }

    public Product getProduct(UUID productId) {
        return products.findById(productId).orElseThrow(() -> new NotFoundException("Product", productId));
    }

    /** Active products that match the text query and, if given, belong to the category. */
    public List<Product> search(String query, UUID categoryId) {
        return products.findAll().stream()
                .filter(Product::isActive)
                .filter(product -> categoryId == null || product.categoryId().equals(categoryId))
                .filter(product -> product.matches(query))
                .sorted(Comparator.comparing(Product::name))
                .toList();
    }

    @Override
    public Optional<VariantView> findVariant(UUID variantId) {
        return products.findByVariantId(variantId)
                .flatMap(product -> product.findVariant(variantId).map(product::toVariantView));
    }
}
