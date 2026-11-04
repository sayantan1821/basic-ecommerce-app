package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class PurchaseOrder {
    @Id private UUID id;
    @Column(name = "reference_id", nullable = false, unique = true) private String referenceId;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String currency;
    @Column(name = "subtotal_paise", nullable = false) private long subtotalPaise;
    @Column(name = "total_paise", nullable = false) private long totalPaise;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PurchaseOrder() {}

    public PurchaseOrder(UUID id, String referenceId, long totalPaise, Instant now) {
        this.id = id;
        this.referenceId = referenceId;
        this.status = "PENDING_PAYMENT";
        this.currency = "INR";
        this.subtotalPaise = totalPaise;
        this.totalPaise = totalPaise;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getReferenceId() { return referenceId; }
    public String getStatus() { return status; }
    public long getSubtotalPaise() { return subtotalPaise; }
    public long getTotalPaise() { return totalPaise; }
}
