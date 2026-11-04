package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.dto.OrderStatusResponse;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByReferenceIdAndUserId(String referenceId, UUID userId);
    Optional<PurchaseOrder> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT new com.basic_ecommerce.app.dto.OrderStatusResponse(o.referenceId, o.status, p.status, o.currency, o.totalPaise) " +
            "FROM PurchaseOrder o, Payment p WHERE p.orderId = o.id AND o.userId = :userId ORDER BY o.createdAt DESC")
    List<OrderStatusResponse> findOrderStatusesByUserId(@Param("userId") UUID userId);
}
