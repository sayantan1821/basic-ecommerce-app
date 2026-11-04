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
    @Column(nullable = false) private String status;
    @Column(name = "amount_paise", nullable = false) private long amountPaise;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "mock_client_secret", nullable = false) private String mockClientSecret;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Payment() {}

    public Payment(UUID id, UUID orderId, String providerReference, long amountPaise, String mockClientSecret, Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.provider = "mock";
        this.providerReference = providerReference;
        this.status = "PENDING";
        this.amountPaise = amountPaise;
        this.mockClientSecret = mockClientSecret;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getMockClientSecret() { return mockClientSecret; }
}
