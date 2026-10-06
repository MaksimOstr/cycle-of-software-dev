package com.cycleofsoftwaredev.cart.application;

import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.cart.domain.PromoCodeRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Use case "Promo code management" for managers. */
@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodes;

    public PromoCodeService(PromoCodeRepository promoCodes) {
        this.promoCodes = promoCodes;
    }

    public PromoCode createPromoCode(String code, int discountPercent, Instant validUntil, int usageLimit) {
        PromoCode promoCode = new PromoCode(code, discountPercent, validUntil, usageLimit);
        if (promoCodes.findByCode(promoCode.code()).isPresent()) {
            throw new BusinessRuleException("Promo code " + promoCode.code() + " already exists");
        }
        return promoCodes.save(promoCode);
    }
}
