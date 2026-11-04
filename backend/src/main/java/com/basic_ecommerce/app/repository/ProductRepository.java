package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.Product;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findAllByActiveTrueOrderByNameAsc();
}
