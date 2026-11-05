package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.OrderStatusResponse;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.List;

@Service
public class OrderStatusService {
    private final PurchaseOrderRepository orders;
    private final PaymentRepository payments;

    public OrderStatusService(PurchaseOrderRepository orders, PaymentRepository payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse byReference(String referenceId, UUID userId) {
        var order = orders.findByReferenceIdAndUserId(referenceId, userId)
                .orElseThrow(() -> new CheckoutException("ORDER_NOT_FOUND", "Order was not found.", HttpStatus.NOT_FOUND));
        var payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException("Order has no payment"));
        return new OrderStatusResponse(order.getReferenceId(), order.getStatus(), payment.getStatus(), order.getCurrency(), order.getTotalPaise(), payment.getPaidAt());
    }

    @Transactional(readOnly = true)
    public List<OrderStatusResponse> listForUser(UUID userId) {
        return orders.findOrderStatusesByUserId(userId);
    }
}
