package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<UUID, Product> products = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        products.put(product.id(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public Optional<Product> findByVariantId(UUID variantId) {
        return products.values().stream()
                .filter(product -> product.findVariant(variantId).isPresent())
                .findFirst();
    }

    @Override
    public boolean existsByVariantSku(String sku) {
        return products.values().stream()
                .flatMap(product -> product.variants().stream())
                .anyMatch(variant -> variant.sku().equalsIgnoreCase(sku));
    }

    @Override
    public List<Product> findAll() {
        return List.copyOf(products.values());
    }

    @Override
    public boolean existsByCategoryId(UUID categoryId) {
        return products.values().stream().anyMatch(product -> product.categoryId().equals(categoryId));
    }

    @Override
    public void deleteById(UUID id) {
        products.remove(id);
    }
}
