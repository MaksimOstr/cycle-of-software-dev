package com.cycleofsoftwaredev.catalog.domain;

import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A product of the catalog with one or more variants. */
public class Product {

    private final UUID id;
    private final List<ProductVariant> variants = new ArrayList<>();
    private String name;
    private String description;
    private UUID categoryId;
    private boolean active = true;

    public Product(UUID id, String name, String description, UUID categoryId) {
        this.id = Objects.requireNonNull(id);
        update(name, description, categoryId);
    }

    public void update(String newName, String newDescription, UUID newCategoryId) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        this.name = newName.trim();
        this.description = newDescription == null ? "" : newDescription.trim();
        this.categoryId = Objects.requireNonNull(newCategoryId);
    }

    public ProductVariant addVariant(String sku, String variantName, Money price) {
        boolean duplicate = variants.stream().anyMatch(variant -> variant.sku().equalsIgnoreCase(sku));
        if (duplicate) {
            throw new BusinessRuleException("Variant with SKU " + sku + " already exists");
        }
        ProductVariant variant = new ProductVariant(UUID.randomUUID(), sku, variantName, price);
        variants.add(variant);
        return variant;
    }

    public Optional<ProductVariant> findVariant(UUID variantId) {
        return variants.stream().filter(variant -> variant.id().equals(variantId)).findFirst();
    }

    /** Simple text match used by the search until PostgreSQL full-text search is added. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String text = (name + " " + description).toLowerCase(Locale.ROOT);
        return text.contains(query.trim().toLowerCase(Locale.ROOT));
    }

    public VariantView toVariantView(ProductVariant variant) {
        return new VariantView(variant.id(), id, variant.sku(), name, variant.name(), variant.price(), active);
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public boolean isActive() {
        return active;
    }

    public List<ProductVariant> variants() {
        return Collections.unmodifiableList(variants);
    }
}
