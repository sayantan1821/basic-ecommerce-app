package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.Product;
import org.springframework.data.jpa.repository.Modifying;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderProductStockRepository extends JpaRepository<Product, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE Product p SET p.stockAvailable = p.stockAvailable - :quantity WHERE p.id = :productId AND p.active = true AND p.stockAvailable >= :quantity")
    int reserveStock(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
