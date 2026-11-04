package com.basic_ecommerce.app.dto;

import com.basic_ecommerce.app.model.Product;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        java.util.List<String> imageUrls,
        long pricePaise,
        int available) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), java.util.List.of(product.getImageUrls()), product.getPricePaise(),
                product.getStockAvailable());
    }
}
