package com.cycleofsoftwaredev.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    Optional<Product> findByVariantId(UUID variantId);

    /** True if a variant of any product already has this article number; the letter case is ignored. */
    boolean existsByVariantSku(String sku);

    List<Product> findAll();

    boolean existsByCategoryId(UUID categoryId);

    void deleteById(UUID id);
}
