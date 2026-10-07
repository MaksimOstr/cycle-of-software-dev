package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.domain.Product;
import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import java.util.List;
import org.springframework.stereotype.Component;

/** Combines catalog data with stock quantities from the Inventory module. */
@Component
public class ProductResponseAssembler {

    private final InventoryApi inventory;

    public ProductResponseAssembler(InventoryApi inventory) {
        this.inventory = inventory;
    }

    public ProductResponse toResponse(Product product) {
        List<ProductResponse.Variant> variants = product.variants().stream()
                .map(variant -> new ProductResponse.Variant(variant.id(), variant.sku(), variant.name(), variant.price(),
                        inventory.availableQuantity(variant.id())))
                .toList();
        return new ProductResponse(product.id(), product.name(), product.description(), product.categoryId(), variants);
    }
}
