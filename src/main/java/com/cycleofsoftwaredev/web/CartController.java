package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.cart.api.CartSnapshot;
import com.cycleofsoftwaredev.cart.application.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartService carts;
    private final CurrentUserResolver currentUser;

    public CartController(CartService carts, CurrentUserResolver currentUser) {
        this.carts = carts;
        this.currentUser = currentUser;
    }

    public record AddItemRequest(@NotNull UUID variantId, @Min(1) @Max(99) int quantity) {
    }

    public record QuantityRequest(@Min(0) @Max(99) int quantity) {
    }

    public record PromoCodeRequest(@NotBlank String code) {
    }

    /** Creates a cart for a guest (no header) or for the signed-in customer. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartSnapshot create(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId) {
        UUID customerId = userId == null ? null : currentUser.requireUser(userId).userId();
        return carts.createCart(customerId);
    }

    @GetMapping("/{cartId}")
    public CartSnapshot get(@PathVariable UUID cartId) {
        return carts.getSnapshot(cartId);
    }

    @PostMapping("/{cartId}/items")
    public CartSnapshot addItem(@PathVariable UUID cartId, @Valid @RequestBody AddItemRequest request) {
        return carts.addItem(cartId, request.variantId(), request.quantity());
    }

    @PutMapping("/{cartId}/items/{variantId}")
    public CartSnapshot updateQuantity(@PathVariable UUID cartId, @PathVariable UUID variantId,
                                       @Valid @RequestBody QuantityRequest request) {
        return carts.updateQuantity(cartId, variantId, request.quantity());
    }

    @DeleteMapping("/{cartId}/items/{variantId}")
    public CartSnapshot removeItem(@PathVariable UUID cartId, @PathVariable UUID variantId) {
        return carts.removeItem(cartId, variantId);
    }

    @PostMapping("/{cartId}/promo-code")
    public CartSnapshot applyPromoCode(@PathVariable UUID cartId, @Valid @RequestBody PromoCodeRequest request) {
        return carts.applyPromoCode(cartId, request.code());
    }
}
