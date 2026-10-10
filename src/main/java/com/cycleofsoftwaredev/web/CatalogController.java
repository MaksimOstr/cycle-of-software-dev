package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog endpoints of the Storefront. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CategoryService categories;
    private final ProductService products;
    private final ProductResponseAssembler assembler;

    public CatalogController(CategoryService categories, ProductService products, ProductResponseAssembler assembler) {
        this.categories = categories;
        this.products = products;
        this.assembler = assembler;
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return categories.listCategories();
    }

    @GetMapping("/products")
    public List<ProductResponse> products(@RequestParam(required = false) String query,
                                          @RequestParam(required = false) UUID categoryId) {
        return products.search(query, categoryId).stream().map(assembler::toResponse).toList();
    }

    @GetMapping("/products/{productId}")
    public ProductResponse product(@PathVariable UUID productId) {
        return assembler.toResponse(products.getProduct(productId));
    }
}
