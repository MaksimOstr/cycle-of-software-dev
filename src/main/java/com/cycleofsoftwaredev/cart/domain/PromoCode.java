package com.cycleofsoftwaredev.cart.domain;

import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Percentage discount code with an expiry date and a usage limit. */
public class PromoCode {

    private final String code;
    private final int discountPercent;
    private final Instant validUntil;
    private final int usageLimit;
    private int usedCount;

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
