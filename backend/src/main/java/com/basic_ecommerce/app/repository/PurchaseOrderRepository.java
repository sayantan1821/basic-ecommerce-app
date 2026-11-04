package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.PurchaseOrder;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByReferenceId(String referenceId);
}
