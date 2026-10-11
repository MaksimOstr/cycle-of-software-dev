package com.cycleofsoftwaredev.cart.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shopping cart of a guest or of a customer. Items keep the order in which they were added. */
@Entity
@Table(name = "carts", schema = "cart")
public class Cart {

    @Id
    private UUID id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cart_items", schema = "cart", joinColumns = @JoinColumn(name = "cart_id"))
    @OrderColumn(name = "position")
    private List<CartItem> items = new ArrayList<>();

    private UUID customerId;
    private String promoCode;
    private Instant updatedAt;

    protected Cart() {
        // for JPA
    }

    public Cart(UUID id, UUID customerId) {
        this.id = Objects.requireNonNull(id);
        this.customerId = customerId;
    }

    /** Sets the quantity of a variant; quantity 0 removes it. */
    public void setQuantity(UUID variantId, int quantity) {
        int index = indexOf(variantId);
        if (quantity == 0) {
            if (index >= 0) {
                items.remove(index);
            }
        } else if (index >= 0) {
            items.set(index, new CartItem(variantId, quantity));
        } else {
            items.add(new CartItem(variantId, quantity));
        }
    }

    public int quantityOf(UUID variantId) {
        int index = indexOf(variantId);
        return index < 0 ? 0 : items.get(index).quantity();
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

    /** Remembers when the cart was changed; carts of guests are removed 30 days after the last change. */
    public void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now);
    }

    public boolean isGuestCart() {
        return customerId == null;
    }

    private int indexOf(UUID variantId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).variantId().equals(variantId)) {
                return i;
            }
        }
        return -1;
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
        return Collections.unmodifiableList(items);
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
