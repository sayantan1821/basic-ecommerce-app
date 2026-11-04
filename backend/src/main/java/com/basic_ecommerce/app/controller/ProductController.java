package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.ProductsResponse;
import com.basic_ecommerce.app.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ProductsResponse listProducts() {
        return new ProductsResponse(productService.listActiveProducts());
    }
}
