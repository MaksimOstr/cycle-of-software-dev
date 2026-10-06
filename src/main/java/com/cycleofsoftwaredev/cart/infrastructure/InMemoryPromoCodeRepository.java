package com.cycleofsoftwaredev.cart.infrastructure;

import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.cart.domain.PromoCodeRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
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
