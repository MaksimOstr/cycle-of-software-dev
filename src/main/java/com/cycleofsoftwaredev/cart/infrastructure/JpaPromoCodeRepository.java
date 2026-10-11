package com.cycleofsoftwaredev.cart.infrastructure;

import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.cart.domain.PromoCodeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of promo codes. */
public interface JpaPromoCodeRepository extends JpaRepository<PromoCode, String>, PromoCodeRepository {
}
