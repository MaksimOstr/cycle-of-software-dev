package com.cycleofsoftwaredev.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    Optional<Product> findByVariantId(UUID variantId);

    List<Product> findAll();
}
