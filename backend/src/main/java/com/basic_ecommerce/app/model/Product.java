package com.basic_ecommerce.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;
    private String sku;
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_urls", columnDefinition = "text[]", nullable = false)
    private String[] imageUrls;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "stock_available", nullable = false)
    private int stockAvailable;

    private boolean active;

    protected Product() {}

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String[] getImageUrls() { return imageUrls == null ? new String[0] : imageUrls.clone(); }
    public long getPricePaise() { return pricePaise; }
    public int getStockAvailable() { return stockAvailable; }
    public boolean isActive() { return active; }
}
