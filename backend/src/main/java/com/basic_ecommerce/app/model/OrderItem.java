package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "sku_snapshot", nullable = false) private String skuSnapshot;
    @Column(name = "product_name_snapshot", nullable = false) private String productNameSnapshot;
    @Column(name = "unit_price_paise", nullable = false) private long unitPricePaise;
    @Column(nullable = false) private int quantity;
    @Column(name = "line_total_paise", nullable = false) private long lineTotalPaise;

    protected OrderItem() {}

    public OrderItem(UUID id, UUID orderId, Product product, int quantity, long lineTotalPaise) {
        this.id = id;
        this.orderId = orderId;
        this.productId = product.getId();
        this.skuSnapshot = product.getSku();
        this.productNameSnapshot = product.getName();
        this.unitPricePaise = product.getPricePaise();
        this.quantity = quantity;
        this.lineTotalPaise = lineTotalPaise;
    }

    public String getProductNameSnapshot() { return productNameSnapshot; }
    public UUID getProductId() { return productId; }
    public long getUnitPricePaise() { return unitPricePaise; }
    public int getQuantity() { return quantity; }
}
