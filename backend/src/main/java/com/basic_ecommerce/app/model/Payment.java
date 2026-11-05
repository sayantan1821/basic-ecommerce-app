package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false, unique = true) private UUID orderId;
    @Column(nullable = false) private String provider;
    @Column(name = "provider_reference", nullable = false, unique = true) private String providerReference;
    @Column(name = "stripe_checkout_session_id", unique = true) private String stripeCheckoutSessionId;
    @Column(name = "stripe_checkout_url") private String stripeCheckoutUrl;
    @Column(name = "stripe_checkout_expires_at") private Instant stripeCheckoutExpiresAt;
    @Column(name = "stripe_payment_intent_id", unique = true) private String stripePaymentIntentId;
    @Column(nullable = false) private String status;
    @Column(name = "amount_paise", nullable = false) private long amountPaise;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Payment() {}

    public Payment(UUID id, UUID orderId, String providerReference, long amountPaise, Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.provider = "stripe";
        this.providerReference = providerReference;
        this.status = "PENDING";
        this.amountPaise = amountPaise;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getStripeCheckoutSessionId() { return stripeCheckoutSessionId; }
    public String getStripeCheckoutUrl() { return stripeCheckoutUrl; }
    public Instant getStripeCheckoutExpiresAt() { return stripeCheckoutExpiresAt; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public long getAmountPaise() { return amountPaise; }
    public String getFailureCode() { return failureCode; }
    public Instant getPaidAt() { return paidAt; }

    public void attachCheckoutSession(String sessionId, String checkoutUrl, Instant expiresAt, Instant now) {
        this.stripeCheckoutSessionId = sessionId;
        this.stripeCheckoutUrl = checkoutUrl;
        this.stripeCheckoutExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void attachPaymentIntent(String paymentIntentId, Instant now) {
        this.stripePaymentIntentId = paymentIntentId;
        if (paymentIntentId != null && !paymentIntentId.isBlank()) this.providerReference = paymentIntentId;
        this.updatedAt = now;
    }

    public void markSucceeded(Instant now) {
        this.status = "SUCCEEDED";
        this.failureCode = null;
        this.paidAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String code, Instant now) {
        this.status = "FAILED";
        this.failureCode = code;
        this.updatedAt = now;
    }
}
