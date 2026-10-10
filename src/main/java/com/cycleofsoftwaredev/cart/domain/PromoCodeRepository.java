package com.cycleofsoftwaredev.cart.domain;

import java.util.Optional;

public interface PromoCodeRepository {

    PromoCode save(PromoCode promoCode);

    Optional<PromoCode> findByCode(String normalizedCode);
}
