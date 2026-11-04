package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CheckoutIdempotencyId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected CheckoutIdempotencyId() {}

    public CheckoutIdempotencyId(UUID userId, String idempotencyKey) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CheckoutIdempotencyId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, idempotencyKey); }
}
