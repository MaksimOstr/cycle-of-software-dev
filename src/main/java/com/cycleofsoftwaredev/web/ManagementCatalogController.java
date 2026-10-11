package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.cart.application.PromoCodeService;
import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.inventory.application.InventoryService;
import com.cycleofsoftwaredev.inventory.domain.StockItem;
import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Management Panel: categories, products, variants, stock and promo codes (managers only). */
@RestController
@RequestMapping("/api/v1/management")
public class ManagementCatalogController {

    private final CategoryService categories;
    private final ProductService products;
    private final InventoryService inventory;
    private final PromoCodeService promoCodes;
    private final ProductResponseAssembler assembler;
    private final CurrentUserResolver currentUser;

    public ManagementCatalogController(CategoryService categories, ProductService products, InventoryService inventory,
                                       PromoCodeService promoCodes, ProductResponseAssembler assembler,
                                       CurrentUserResolver currentUser) {
        this.categories = categories;
        this.products = products;
        this.inventory = inventory;
        this.promoCodes = promoCodes;
        this.assembler = assembler;
        this.currentUser = currentUser;
    }

    public record CategoryRequest(@NotBlank String name, UUID parentId) {
    }

    public record CategoryNameRequest(@NotBlank String name) {
    }

    public record ProductRequest(@NotBlank String name, String description, @NotNull UUID categoryId) {
    }

    public record VisibilityRequest(@NotNull Boolean visible) {
    }

    public record VariantRequest(@NotBlank String sku, @NotBlank String name, @NotNull @DecimalMin("0.01") BigDecimal price) {
    }

    public record StockRequest(@Min(0) int onHand) {
    }

    public record StockResponse(UUID variantId, int onHand, int reserved, int available, boolean lowStock) {
    }

    public record PromoCodeRequest(@NotBlank String code, @Min(1) @Max(90) int discountPercent,
                                   @NotNull @Future Instant validUntil, @Min(1) int usageLimit) {
    }

    public record PromoCodeResponse(String code, int discountPercent, int usedCount) {
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        currentUser.requireStaff();
        return CategoryResponse.of(categories.createCategory(request.name(), request.parentId()));
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryResponse renameCategory(@PathVariable UUID categoryId,
                                           @Valid @RequestBody CategoryNameRequest request) {
        currentUser.requireStaff();
        return CategoryResponse.of(categories.renameCategory(categoryId, request.name()));
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID categoryId) {
        currentUser.requireStaff();
        categories.deleteCategory(categoryId);
    }

    /** All products including hidden ones. */
    @GetMapping("/products")
    public List<ProductResponse> allProducts() {
        currentUser.requireStaff();
        return assembler.toResponses(products.allProducts());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        currentUser.requireStaff();
        return assembler.toResponse(products.createProduct(request.name(), request.description(), request.categoryId()));
    }

    @PutMapping("/products/{productId}")
    public ProductResponse updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductRequest request) {
        currentUser.requireStaff();
        return assembler.toResponse(
                products.updateProduct(productId, request.name(), request.description(), request.categoryId()));
    }

    /** Hides the product from customers or shows it again. */
    @PatchMapping("/products/{productId}/visibility")
    public ProductResponse setVisibility(
            @PathVariable UUID productId,
            @Valid @RequestBody VisibilityRequest request) {
        currentUser.requireStaff();
        return assembler.toResponse(products.setVisible(productId, request.visible()));
    }

    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable UUID productId) {
        currentUser.requireStaff();
        products.deleteProduct(productId);
    }

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse addVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody VariantRequest request) {
        currentUser.requireStaff();
        products.addVariant(productId, request.sku(), request.name(), Money.of(request.price()));
        return assembler.toResponse(products.getProduct(productId));
    }

    @PutMapping("/stock/{variantId}")
    public StockResponse setStock(@PathVariable UUID variantId,
                                  @Valid @RequestBody StockRequest request) {
        currentUser.requireStaff();
        StockItem item = inventory.setStock(variantId, request.onHand());
        return new StockResponse(item.variantId(), item.onHand(), item.reserved(), item.available(), item.isLowStock());
    }

    @PostMapping("/promo-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeResponse createPromoCode(@Valid @RequestBody PromoCodeRequest request) {
        currentUser.requireStaff();
        PromoCode promoCode = promoCodes.createPromoCode(request.code(), request.discountPercent(),
                request.validUntil(), request.usageLimit());
        return new PromoCodeResponse(promoCode.code(), promoCode.discountPercent(), promoCode.usedCount());
    }
}
