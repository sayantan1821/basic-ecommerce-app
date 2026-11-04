package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.ProductResponse;
import com.basic_ecommerce.app.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listActiveProducts() {
        return products.findAllByActiveTrueOrderByNameAsc().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
