package com.cycleofsoftwaredev.payment.domain;

import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Online card payment of one order. Card data never reaches ShopSphere, only the provider session id. */
@Entity
@Table(name = "payments", schema = "payment")
public class Payment {

    @Id
    private UUID id;
    private UUID orderId;
    private String providerSessionId;

    @Column(length = 500)
    private String paymentUrl;

    private Money amount;
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    protected Payment() {
        // for JPA
    }

    public Payment(UUID id, UUID orderId, String providerSessionId, String paymentUrl, Money amount, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.providerSessionId = Objects.requireNonNull(providerSessionId);
        this.paymentUrl = Objects.requireNonNull(paymentUrl);
        this.amount = Objects.requireNonNull(amount);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /**
     * The provider confirmed the payment. A cancelled payment can still succeed if the customer paid just
     * before the session expired; the order is then refunded by the Ordering module.
     */
    public void markSucceeded() {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.CANCELLED) {
            throw new BusinessRuleException("Payment " + id + " is " + status + " and cannot succeed");
        }
        status = PaymentStatus.SUCCEEDED;
    }

    public void markCancelled() {
        requireStatus(PaymentStatus.PENDING);
        status = PaymentStatus.CANCELLED;
    }

    public void markRefunded() {
        requireStatus(PaymentStatus.SUCCEEDED);
        status = PaymentStatus.REFUNDED;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new BusinessRuleException("Payment " + id + " is " + status + ", expected " + expected);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public String providerSessionId() {
        return providerSessionId;
    }

    public String paymentUrl() {
        return paymentUrl;
    }

    public Money amount() {
        return amount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public PaymentStatus status() {
        return status;
    }
}
