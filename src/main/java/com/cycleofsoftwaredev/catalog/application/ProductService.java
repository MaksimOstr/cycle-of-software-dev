package com.cycleofsoftwaredev.catalog.application;

import com.cycleofsoftwaredev.catalog.api.CatalogApi;
import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    public Product updateProduct(UUID productId, String name, String description, UUID categoryId) {
        Product product = getProduct(productId);
        categories.getCategory(categoryId);
        product.update(name, description, categoryId);
        return products.save(product);
    }

    /** Hidden products are not visible to customers and cannot be added to a cart. */
    public Product setVisible(UUID productId, boolean visible) {
        Product product = getProduct(productId);
        if (visible) {
            product.activate();
        } else {
            product.deactivate();
        }
        return products.save(product);
    }

    /** Orders keep their own copy of the product name and price, so they are not affected. */
    public void deleteProduct(UUID productId) {
        products.delete(getProduct(productId).id());
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

    /** Product page of the storefront: a hidden product looks like a missing one. */
    public Product getVisibleProduct(UUID productId) {
        return products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    /** All products including hidden ones, for the Management Panel. */
    public List<Product> allProducts() {
        return products.findAll().stream().sorted(ProductSort.NAME.comparator()).toList();
    }

    /** Active products that match the text query and, if given, belong to the category or its subcategories. */
    public List<Product> search(String query, UUID categoryId) {
        return search(query, categoryId, ProductSort.NAME);
    }

    public List<Product> search(String query, UUID categoryId, ProductSort sort) {
        Set<UUID> branch = categoryId == null ? null : categories.branchIds(categoryId);
        return products.findAll().stream()
                .filter(Product::isActive)
                .filter(product -> branch == null || branch.contains(product.categoryId()))
                .filter(product -> product.matches(query))
                .sorted(sort.comparator())
                .toList();
    }

    public ProductPage searchPage(String query, UUID categoryId, ProductSort sort, int page, int size) {
        return ProductPage.of(search(query, categoryId, sort), page, size);
    }

    @Override
    public Optional<VariantView> findVariant(UUID variantId) {
        return products.findByVariantId(variantId)
                .flatMap(product -> product.findVariant(variantId).map(product::toVariantView));
    }
}
