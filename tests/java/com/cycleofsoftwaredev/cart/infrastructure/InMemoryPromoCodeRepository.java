package com.cycleofsoftwaredev.cart.infrastructure;

import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.cart.domain.PromoCodeRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryPromoCodeRepository implements PromoCodeRepository {

    private final Map<String, PromoCode> promoCodes = new ConcurrentHashMap<>();

    @Override
    public PromoCode save(PromoCode promoCode) {
        promoCodes.put(promoCode.code(), promoCode);
        return promoCode;
    }

    @Override
    public Optional<PromoCode> findByCode(String normalizedCode) {
        return Optional.ofNullable(promoCodes.get(normalizedCode));
    }
}
