package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.payment.application.PaymentService;
import com.cycleofsoftwaredev.payment.application.PaymentWebhookEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment provider notifications. Verification of the Stripe-Signature header is added together with the
 * Stripe adapter (work item SHOP-39).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    private final PaymentService payments;

    public PaymentWebhookController(PaymentService payments) {
        this.payments = payments;
    }

    public record WebhookRequest(@NotBlank String id, @NotBlank String type, @NotBlank String sessionId) {
    }

    @PostMapping("/webhook")
    public void webhook(@Valid @RequestBody WebhookRequest request) {
        payments.handleWebhook(new PaymentWebhookEvent(request.id(), request.type(), request.sessionId()));
    }
}
