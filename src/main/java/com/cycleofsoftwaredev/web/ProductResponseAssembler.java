package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.catalog.domain.ProductVariant;
import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Combines catalog data with stock quantities from the Inventory module. */
@Component
public class ProductResponseAssembler {

    private final InventoryApi inventory;

    public ProductResponseAssembler(InventoryApi inventory) {
        this.inventory = inventory;
    }

    public ProductResponse toResponse(Product product) {
        return toResponses(List.of(product)).getFirst();
    }

    /** The stock of all variants of the list is read with one request to the Inventory module. */
    public List<ProductResponse> toResponses(List<Product> products) {
        List<UUID> variantIds = products.stream()
                .flatMap(product -> product.variants().stream())
                .map(ProductVariant::id)
                .toList();
        Map<UUID, Integer> available = inventory.availableQuantities(variantIds);
        return products.stream().map(product -> toResponse(product, available)).toList();
    }

    private static ProductResponse toResponse(Product product, Map<UUID, Integer> available) {
        List<ProductResponse.Variant> variants = product.variants().stream()
                .map(variant -> new ProductResponse.Variant(variant.id(), variant.sku(), variant.name(), variant.price(),
                        available.getOrDefault(variant.id(), 0)))
                .toList();
        return new ProductResponse(product.id(), product.name(), product.description(), product.categoryId(),
                product.isActive(), variants);
    }
}
