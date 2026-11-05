package com.basic_ecommerce.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrderStatusServiceTest {
    private PurchaseOrderRepository orders;
    private PaymentRepository payments;
    private OrderStatusService service;

    @BeforeEach
    void setUp() {
        orders = org.mockito.Mockito.mock(PurchaseOrderRepository.class);
        payments = org.mockito.Mockito.mock(PaymentRepository.class);
        service = new OrderStatusService(orders, payments);
    }

    @Test
    void orderLookupIsScopedToRequestingUserAndHidesAnotherUsersOrder() {
        String referenceId = "order-ref-private";
        UUID requestingUserId = UUID.randomUUID();
        when(orders.findByReferenceIdAndUserId(referenceId, requestingUserId)).thenReturn(Optional.empty());

        CheckoutException exception = assertThrows(CheckoutException.class,
                () -> service.byReference(referenceId, requestingUserId));

        assertEquals("ORDER_NOT_FOUND", exception.getCode());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(orders).findByReferenceIdAndUserId(referenceId, requestingUserId);
        verifyNoInteractions(payments);
    }

    @Test
    void orderHistoryDelegatesFilteringToTheAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();

        service.listForUser(userId);

        verify(orders).findOrderStatusesByUserId(userId);
        verifyNoInteractions(payments);
    }

    @Test
    void returnsPaymentAndOrderStatusForTheOrderOwner() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        PurchaseOrder order = new PurchaseOrder(orderId, "order-ref-owned", 2500L, userId, now);
        Payment payment = new Payment(UUID.randomUUID(), orderId, "provider-ref", 2500L, now);
        when(orders.findByReferenceIdAndUserId("order-ref-owned", userId)).thenReturn(Optional.of(order));
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        var result = service.byReference("order-ref-owned", userId);

        assertEquals("PENDING_PAYMENT", result.orderStatus());
        assertEquals("PENDING", result.paymentStatus());
        assertEquals(2500L, result.totalPaise());
        verify(orders).findByReferenceIdAndUserId("order-ref-owned", userId);
        verify(payments).findByOrderId(orderId);
    }
}
