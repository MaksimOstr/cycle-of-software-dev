package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PostgreSQL storage of products with their variants. */
public interface JpaProductRepository extends JpaRepository<Product, UUID>, ProductRepository {

    @Override
    @Query("select p from Product p join p.variants v where v.id = :variantId")
    Optional<Product> findByVariantId(@Param("variantId") UUID variantId);

    @Override
    @Query("select count(p) > 0 from Product p join p.variants v where lower(v.sku) = lower(:sku)")
    boolean existsByVariantSku(@Param("sku") String sku);
}
