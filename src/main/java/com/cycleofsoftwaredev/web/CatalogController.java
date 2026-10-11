package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductPage;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.application.ProductSort;
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

    public record ProductPageResponse(List<ProductResponse> items, int page, int size, int totalItems, int totalPages) {
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return categories.listCategories();
    }

    /** Product listing and search: one page of products, 24 per page by default. */
    @GetMapping("/products")
    public ProductPageResponse products(@RequestParam(required = false) String query,
                                        @RequestParam(required = false) UUID categoryId,
                                        @RequestParam(defaultValue = "NAME") ProductSort sort,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "" + ProductPage.DEFAULT_SIZE) int size) {
        ProductPage result = products.searchPage(query, categoryId, sort, page, size);
        return new ProductPageResponse(result.items().stream().map(assembler::toResponse).toList(),
                result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    @GetMapping("/products/{productId}")
    public ProductResponse product(@PathVariable UUID productId) {
        return assembler.toResponse(products.getVisibleProduct(productId));
    }
}
