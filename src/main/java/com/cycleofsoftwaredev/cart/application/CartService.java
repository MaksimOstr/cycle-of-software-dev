package com.cycleofsoftwaredev.cart.application;

import com.cycleofsoftwaredev.cart.api.CartApi;
import com.cycleofsoftwaredev.cart.api.CartLine;
import com.cycleofsoftwaredev.cart.api.CartSnapshot;
import com.cycleofsoftwaredev.cart.domain.Cart;
import com.cycleofsoftwaredev.cart.domain.CartItem;
import com.cycleofsoftwaredev.cart.domain.CartRepository;
import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.cart.domain.PromoCodeRepository;
import com.cycleofsoftwaredev.catalog.api.CatalogApi;
import com.cycleofsoftwaredev.catalog.api.VariantView;
import com.cycleofsoftwaredev.inventory.api.InventoryApi;
import com.cycleofsoftwaredev.inventory.api.OutOfStockException;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use cases "Add to cart", "Manage cart" and "Promo codes". Depends only on the APIs of Catalog and Inventory. */
@Service
public class CartService implements CartApi {

    private final CartRepository carts;
    private final PromoCodeRepository promoCodes;
    private final CatalogApi catalog;
    private final InventoryApi inventory;
    private final Clock clock;

    public CartService(CartRepository carts, PromoCodeRepository promoCodes, CatalogApi catalog,
                       InventoryApi inventory, Clock clock) {
        this.carts = carts;
        this.promoCodes = promoCodes;
        this.catalog = catalog;
        this.inventory = inventory;
        this.clock = clock;
    }

    public CartSnapshot createCart(UUID customerId) {
        Cart cart = carts.save(new Cart(UUID.randomUUID(), customerId));
        return snapshot(cart);
    }

    public CartSnapshot addItem(UUID cartId, UUID variantId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        Cart cart = getCart(cartId);
        return changeQuantity(cart, variantId, cart.quantityOf(variantId) + quantity);
    }

    public CartSnapshot updateQuantity(UUID cartId, UUID variantId, int quantity) {
        return changeQuantity(getCart(cartId), variantId, quantity);
    }

    public CartSnapshot removeItem(UUID cartId, UUID variantId) {
        return changeQuantity(getCart(cartId), variantId, 0);
    }

    public CartSnapshot applyPromoCode(UUID cartId, String code) {
        Cart cart = getCart(cartId);
        PromoCode promoCode = usablePromoCode(code)
                .orElseThrow(() -> new BusinessRuleException("Promo code " + code + " is invalid or expired"));
        cart.applyPromoCode(promoCode.code());
        return snapshot(carts.save(cart));
    }

    public CartSnapshot getSnapshot(UUID cartId) {
        return snapshot(getCart(cartId));
    }

    @Override
    public CartSnapshot getCheckoutCart(UUID cartId) {
        return getSnapshot(cartId);
    }

    @Override
    public void completeCheckout(UUID cartId, UUID orderId) {
        Cart cart = getCart(cartId);
        cart.promoCode().flatMap(promoCodes::findByCode).ifPresent(promoCode -> {
            promoCode.redeem();
            promoCodes.save(promoCode);
        });
        cart.clear();
        carts.save(cart);
    }

    private CartSnapshot changeQuantity(Cart cart, UUID variantId, int quantity) {
        if (quantity > 0) {
            VariantView variant = catalog.findVariant(variantId)
                    .filter(VariantView::available)
                    .orElseThrow(() -> new NotFoundException("Product variant", variantId));
            int available = inventory.availableQuantity(variant.variantId());
            if (available < quantity) {
                throw new OutOfStockException(variantId, available);
            }
        }
        cart.setQuantity(variantId, quantity);
        return snapshot(carts.save(cart));
    }

    private CartSnapshot snapshot(Cart cart) {
        List<CartLine> lines = cart.items().stream().map(this::toLine).flatMap(Optional::stream).toList();
        Money subtotal = lines.stream().map(CartLine::lineTotal).reduce(Money.zero(), Money::add);
        Optional<PromoCode> promoCode = cart.promoCode().flatMap(this::usablePromoCode);
        Money discount = promoCode.map(code -> code.discountFor(subtotal)).orElse(Money.zero());
        return new CartSnapshot(cart.id(), lines, subtotal, promoCode.map(PromoCode::code).orElse(null), discount);
    }

    private Optional<CartLine> toLine(CartItem item) {
        // a variant removed from the catalog silently disappears from the cart
        return catalog.findVariant(item.variantId())
                .filter(VariantView::available)
                .map(variant -> new CartLine(variant.variantId(), variant.productId(), variant.sku(),
                        variant.productName() + " (" + variant.variantName() + ")", variant.price(), item.quantity()));
    }

    private Optional<PromoCode> usablePromoCode(String code) {
        return promoCodes.findByCode(PromoCode.normalize(code)).filter(promo -> promo.isUsable(clock.instant()));
    }

    private Cart getCart(UUID cartId) {
        return carts.findById(cartId).orElseThrow(() -> new NotFoundException("Cart", cartId));
    }
}
