package com.cycleofsoftwaredev.catalog.application;

import com.cycleofsoftwaredev.catalog.api.CatalogApi;
import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import com.cycleofsoftwaredev.catalog.domain.ProductSearch;
import com.cycleofsoftwaredev.catalog.domain.ProductSort;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases "Product management", "Product variants", "Product listing" and "Product search". */
@Service
@Transactional
public class ProductService implements CatalogApi {

    private final ProductRepository products;
    private final ProductSearch productSearch;
    private final CategoryService categories;

    public ProductService(ProductRepository products, ProductSearch productSearch, CategoryService categories) {
        this.products = products;
        this.productSearch = productSearch;
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
        products.deleteById(getProduct(productId).id());
    }

    public ProductVariant addVariant(UUID productId, String sku, String variantName, Money price) {
        Product product = getProduct(productId);
        // requirement "Product variants": the article number belongs to one variant in the whole catalog
        if (sku != null && products.existsByVariantSku(sku)) {
            throw new BusinessRuleException("Variant with SKU " + sku + " already exists");
        }
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

    /**
     * One page of the visible products that match the text query and, if a category is given, belong to it
     * or to its subcategories. The storage filters, sorts and cuts the page; only its products are loaded.
     */
    public ProductPage searchPage(String query, UUID categoryId, ProductSort sort, int page, int size) {
        int offset = ProductPage.offsetOf(page, size);
        String text = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        Set<UUID> branch = categoryId == null ? null : categories.branchIds(categoryId);
        List<Product> items = productSearch.find(text, branch, sort, offset, size);
        return new ProductPage(items, page, size, productSearch.count(text, branch));
    }

    @Override
    public Optional<VariantView> findVariant(UUID variantId) {
        return products.findByVariantId(variantId)
                .flatMap(product -> product.findVariant(variantId).map(product::toVariantView));
    }
}
