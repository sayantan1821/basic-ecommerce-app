package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_idempotency")
public class CheckoutIdempotency {
    @EmbeddedId private CheckoutIdempotencyId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutIdempotency() {}

    public CheckoutIdempotency(CheckoutIdempotencyId id, String requestHash, UUID orderId, Instant createdAt) {
        this.id = id;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getOrderId() { return orderId; }
}
