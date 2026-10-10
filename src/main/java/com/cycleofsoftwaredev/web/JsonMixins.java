package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.cart.api.CartSnapshot;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * JSON representation of shared types. Kept in the web layer, so the domain classes stay free of
 * serialization annotations.
 */
final class JsonMixins {

    private JsonMixins() {
    }

    /** Money is sent as {@code {"amount": 1299.00, "currency": "UAH"}}. */
    @JacksonMixin(Money.class)
    @JsonIgnoreProperties({"negative", "zero"})
    abstract static class MoneyMixin {
    }

    @JacksonMixin(CartSnapshot.class)
    @JsonIgnoreProperties({"empty"})
    abstract static class CartSnapshotMixin {
    }
}
