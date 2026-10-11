package com.cycleofsoftwaredev.cart.domain;

import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Percentage discount code with an expiry date and a usage limit. */
@Entity
@Table(name = "promo_codes", schema = "cart")
public class PromoCode {

    @Id
    private String code;
    private int discountPercent;
    private Instant validUntil;
    private int usageLimit;
    private int usedCount;

    protected PromoCode() {
        // for JPA
    }

    public PromoCode(String code, int discountPercent, Instant validUntil, int usageLimit) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Promo code must not be blank");
        }
        if (discountPercent < 1 || discountPercent > 90) {
            throw new IllegalArgumentException("Discount must be between 1 and 90 percent");
        }
        if (usageLimit < 1) {
            throw new IllegalArgumentException("Usage limit must be positive");
        }
        this.code = normalize(code);
        this.discountPercent = discountPercent;
        this.validUntil = Objects.requireNonNull(validUntil);
        this.usageLimit = usageLimit;
    }

    public static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isUsable(Instant now) {
        return now.isBefore(validUntil) && usedCount < usageLimit;
    }

    public Money discountFor(Money subtotal) {
        return subtotal.percent(discountPercent);
    }

    public void redeem() {
        usedCount++;
    }

    public String code() {
        return code;
    }

    public int discountPercent() {
        return discountPercent;
    }

    public int usedCount() {
        return usedCount;
    }
}
