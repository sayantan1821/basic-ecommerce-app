package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_idempotency")
public class CheckoutIdempotency {
    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutIdempotency() {}

    public CheckoutIdempotency(String key, String requestHash, UUID orderId, Instant createdAt) {
        this.idempotencyKey = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getOrderId() { return orderId; }
}
