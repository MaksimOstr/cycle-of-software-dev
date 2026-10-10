package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.application.CategoryService;
import com.cycleofsoftwaredev.catalog.application.ProductService;
import com.cycleofsoftwaredev.catalog.domain.Category;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    public record ProductRequest(@NotBlank String name, String description, @NotNull UUID categoryId) {
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
    public Category createCategory(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                   @Valid @RequestBody CategoryRequest request) {
        currentUser.requireStaff(userId);
        return categories.createCategory(request.name(), request.parentId());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
            @Valid @RequestBody ProductRequest request) {
        currentUser.requireStaff(userId);
        return assembler.toResponse(products.createProduct(request.name(), request.description(), request.categoryId()));
    }

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse addVariant(
            @PathVariable UUID productId,
            @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
            @Valid @RequestBody VariantRequest request) {
        currentUser.requireStaff(userId);
        products.addVariant(productId, request.sku(), request.name(), Money.of(request.price()));
        return assembler.toResponse(products.getProduct(productId));
    }

    @PutMapping("/stock/{variantId}")
    public StockResponse setStock(@PathVariable UUID variantId,
                                  @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                  @Valid @RequestBody StockRequest request) {
        currentUser.requireStaff(userId);
        StockItem item = inventory.setStock(variantId, request.onHand());
        return new StockResponse(item.variantId(), item.onHand(), item.reserved(), item.available(), item.isLowStock());
    }

    @PostMapping("/promo-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeResponse createPromoCode(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                             @Valid @RequestBody PromoCodeRequest request) {
        currentUser.requireStaff(userId);
        PromoCode promoCode = promoCodes.createPromoCode(request.code(), request.discountPercent(),
                request.validUntil(), request.usageLimit());
        return new PromoCodeResponse(promoCode.code(), promoCode.discountPercent(), promoCode.usedCount());
    }
}
