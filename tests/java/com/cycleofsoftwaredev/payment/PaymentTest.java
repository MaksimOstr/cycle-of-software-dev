package com.cycleofsoftwaredev.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.payment.api.PaymentRequest;
import com.cycleofsoftwaredev.payment.domain.Payment;
import com.cycleofsoftwaredev.payment.domain.PaymentStatus;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Statuses of an online payment: pending, succeeded, cancelled, refunded. */
class PaymentTest {

    private final Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), "cs_test_1",
            "https://pay.test/cs_test_1", Money.of("1880.00"), Instant.parse("2026-10-05T10:00:00Z"));

    @Test
    void newPaymentWaitsForTheCustomer() {
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.amount()).isEqualTo(Money.of("1880.00"));
        assertThat(payment.providerSessionId()).isEqualTo("cs_test_1");
    }

    @Test
    void paidPaymentCanBeRefundedOnce() {
        payment.markSucceeded();
        payment.markRefunded();

        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(payment::markRefunded).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(payment::markSucceeded).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void paymentCannotSucceedTwice() {
        payment.markSucceeded();

        assertThatThrownBy(payment::markSucceeded).isInstanceOf(BusinessRuleException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void onlyAPendingPaymentCanBeCancelled() {
        payment.markSucceeded();

        assertThatThrownBy(payment::markCancelled).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void moneyThatWasNotPaidCannotBeRefunded() {
        assertThatThrownBy(payment::markRefunded).isInstanceOf(BusinessRuleException.class);

        payment.markCancelled();

        assertThatThrownBy(payment::markRefunded).isInstanceOf(BusinessRuleException.class);
    }

    /** The customer paid just before the session expired: the money has arrived and must be returned. */
    @Test
    void cancelledPaymentCanStillSucceed() {
        payment.markCancelled();

        payment.markSucceeded();

        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100"})
    void paymentIsRequestedOnlyForAPositiveAmount(String amount) {
        assertThatThrownBy(() -> new PaymentRequest(UUID.randomUUID(), "SS-1", Money.of(amount)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paymentRequestNeedsTheOrder() {
        assertThatThrownBy(() -> new PaymentRequest(null, "SS-1", Money.of("10"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentRequest(UUID.randomUUID(), null, Money.of("10"))).isInstanceOf(NullPointerException.class);
        assertThat(new PaymentRequest(UUID.randomUUID(), "SS-1", Money.of("0.01")).amount()).isEqualTo(Money.of("0.01"));
    }
}
