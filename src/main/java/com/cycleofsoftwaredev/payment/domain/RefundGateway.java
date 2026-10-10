package com.cycleofsoftwaredev.payment.domain;

import com.cycleofsoftwaredev.shared.domain.Money;

/** Port to the payment provider for returning money. */
public interface RefundGateway {

    void refund(String sessionId, Money amount);
}
