package com.cycleofsoftwaredev.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.cart.domain.PromoCode;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import com.cycleofsoftwaredev.support.ShopFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Requirement "Promo codes": percentage discount, validity period, usage limit. */
class PromoCodeTest {

    private static final Instant VALID_UNTIL = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void codeIsStoredInUpperCaseWithoutSpaces() {
        PromoCode promoCode = new PromoCode("  welcome10 ", 10, VALID_UNTIL, 100);

        assertThat(promoCode.code()).isEqualTo("WELCOME10");
        assertThat(promoCode.discountPercent()).isEqualTo(10);
        assertThat(promoCode.usedCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"1000.00, 10, 100.00", "999.99, 15, 150.00", "0.05, 50, 0.03", "100.00, 90, 90.00", "0.00, 10, 0.00"})
    void discountIsThePercentageOfTheSubtotalRoundedToKopecks(String subtotal, int percent, String discount) {
        PromoCode promoCode = new PromoCode("SALE", percent, VALID_UNTIL, 1);

        assertThat(promoCode.discountFor(Money.of(subtotal))).isEqualTo(Money.of(discount));
    }

    @Test
    void worksUntilItsLastMoment() {
        PromoCode promoCode = new PromoCode("SALE", 10, VALID_UNTIL, 1);

        assertThat(promoCode.isUsable(VALID_UNTIL.minusSeconds(1))).isTrue();
        assertThat(promoCode.isUsable(VALID_UNTIL)).isFalse();
        assertThat(promoCode.isUsable(VALID_UNTIL.plusSeconds(1))).isFalse();
    }

    @Test
    void worksUntilTheUsageLimitIsReached() {
        PromoCode promoCode = new PromoCode("SALE", 10, VALID_UNTIL, 2);
        Instant now = VALID_UNTIL.minus(Duration.ofDays(1));

        promoCode.redeem();
        assertThat(promoCode.isUsable(now)).isTrue();
        promoCode.redeem();

        assertThat(promoCode.isUsable(now)).isFalse();
        assertThat(promoCode.usedCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, 0, 91, 100})
    void discountIsFromOneToNinetyPercent(int percent) {
        assertThatThrownBy(() -> new PromoCode("SALE", percent, VALID_UNTIL, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codeAndUsageLimitAreRequired() {
        assertThatThrownBy(() -> new PromoCode(" ", 10, VALID_UNTIL, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromoCode(null, 10, VALID_UNTIL, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromoCode("SALE", 10, VALID_UNTIL, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromoCode("SALE", 10, null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void codeWithTheSameNameCannotBeCreatedTwice() {
        ShopFixture shop = new ShopFixture();
        shop.promoCodes.createPromoCode("SALE", 10, VALID_UNTIL, 1);

        assertThatThrownBy(() -> shop.promoCodes.createPromoCode(" sale ", 20, VALID_UNTIL, 5))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void codeThatWasUsedUpStopsGivingTheDiscount() {
        ShopFixture shop = new ShopFixture();
        UUID hoodie = shop.variant("HOOD-M", "1000", 10);
        shop.promoCodes.createPromoCode("ONCE", 10, shop.clock.instant().plus(Duration.ofDays(1)), 1);
        UUID firstCart = shop.cartWith(hoodie, 1);
        UUID secondCart = shop.cartWith(hoodie, 1);
        shop.carts.applyPromoCode(firstCart, "once");
        shop.carts.applyPromoCode(secondCart, "once");

        shop.carts.completeCheckout(firstCart, UUID.randomUUID());

        assertThat(shop.carts.getSnapshot(secondCart).discount()).isEqualTo(Money.zero());
        assertThat(shop.carts.getSnapshot(secondCart).promoCode()).isNull();
        assertThatThrownBy(() -> shop.carts.applyPromoCode(secondCart, "ONCE")).isInstanceOf(BusinessRuleException.class);
    }
}
