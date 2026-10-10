package com.cycleofsoftwaredev.cart.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shopping cart of a guest or of a customer. */
public class Cart {

    private final UUID id;
    private final Map<UUID, CartItem> items = new LinkedHashMap<>();
    private UUID customerId;
    private String promoCode;

    public Cart(UUID id, UUID customerId) {
        this.id = Objects.requireNonNull(id);
        this.customerId = customerId;
    }

    /** Sets the quantity of a variant; quantity 0 removes it. */
    public void setQuantity(UUID variantId, int quantity) {
        if (quantity == 0) {
            items.remove(variantId);
        } else {
            items.put(variantId, new CartItem(variantId, quantity));
        }
    }

    public int quantityOf(UUID variantId) {
        return Optional.ofNullable(items.get(variantId)).map(CartItem::quantity).orElse(0);
    }

    public void applyPromoCode(String code) {
        this.promoCode = code;
    }

    public void assignTo(UUID newCustomerId) {
        this.customerId = Objects.requireNonNull(newCustomerId);
    }

    public void clear() {
        items.clear();
        promoCode = null;
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public Optional<String> promoCode() {
        return Optional.ofNullable(promoCode);
    }

    public Collection<CartItem> items() {
        return Collections.unmodifiableCollection(items.values());
    }
}
