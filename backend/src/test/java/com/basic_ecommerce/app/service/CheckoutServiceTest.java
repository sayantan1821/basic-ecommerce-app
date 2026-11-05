package com.basic_ecommerce.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.basic_ecommerce.app.dto.CheckoutItemRequest;
import com.basic_ecommerce.app.dto.CheckoutRequest;
import com.basic_ecommerce.app.dto.CheckoutResponse;
import com.basic_ecommerce.app.model.CheckoutIdempotency;
import com.basic_ecommerce.app.model.CheckoutIdempotencyId;
import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.Product;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.CheckoutIdempotencyRepository;
import com.basic_ecommerce.app.repository.OrderItemRepository;
import com.basic_ecommerce.app.repository.OrderProductStockRepository;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.ProductRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class CheckoutServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private JdbcTemplate jdbcTemplate;
    private ProductRepository products;
    private OrderProductStockRepository stock;
    private PurchaseOrderRepository orders;
    private OrderItemRepository orderItems;
    private PaymentRepository payments;
    private CheckoutIdempotencyRepository idempotencies;
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        products = org.mockito.Mockito.mock(ProductRepository.class);
        stock = org.mockito.Mockito.mock(OrderProductStockRepository.class);
        orders = org.mockito.Mockito.mock(PurchaseOrderRepository.class);
        orderItems = org.mockito.Mockito.mock(OrderItemRepository.class);
        payments = org.mockito.Mockito.mock(PaymentRepository.class);
        idempotencies = org.mockito.Mockito.mock(CheckoutIdempotencyRepository.class);
        service = new CheckoutService(jdbcTemplate, products, stock, orders, orderItems, payments, idempotencies);
    }

    @Test
    void rejectsInvalidKeyAndCartShapesBeforeTouchingPersistence() {
        CheckoutException blankKey = assertThrows(CheckoutException.class,
                () -> service.checkout("  ", request(item(productId, 1)), userId));
        assertEquals("INVALID_IDEMPOTENCY_KEY", blankKey.getCode());

        CheckoutException emptyCart = assertThrows(CheckoutException.class,
                () -> service.checkout("key-empty", new CheckoutRequest(List.of()), userId));
        assertEquals("INVALID_CART", emptyCart.getCode());

        CheckoutException invalidQuantity = assertThrows(CheckoutException.class,
                () -> service.checkout("key-quantity", request(item(productId, 11)), userId));
        assertEquals("INVALID_CART_ITEM", invalidQuantity.getCode());

        CheckoutException duplicateProduct = assertThrows(CheckoutException.class,
                () -> service.checkout("key-duplicate", request(item(productId, 1), item(productId, 2)), userId));
        assertEquals("DUPLICATE_PRODUCT", duplicateProduct.getCode());

        verifyNoInteractions(jdbcTemplate, products, stock, orders, orderItems, payments, idempotencies);
    }

    @Test
    void reservesStockAndPersistsOrderPaymentAndIdempotencyMapping() {
        prepareSuccessfulCheckout();

        CheckoutResponse response = service.checkout("checkout-1", request(item(productId, 2)), userId);

        assertEquals("PENDING_PAYMENT", response.order().status());
        assertEquals(500L, response.order().totalPaise());
        assertEquals("PENDING", response.payment().status());
        assertEquals(false, response.replayed());
        verify(stock).reserveStock(productId, 2);
        verify(orders).saveAndFlush(any(PurchaseOrder.class));
        verify(orderItems).saveAll(anyCollection());
        verify(payments).save(any(Payment.class));
        verify(idempotencies).save(any(CheckoutIdempotency.class));
    }

    @Test
    void replaysSameIdempotencyKeyAndCartWithoutReservingStockAgain() {
        AtomicReference<CheckoutIdempotency> savedMapping = new AtomicReference<>();
        AtomicReference<PurchaseOrder> savedOrder = new AtomicReference<>();
        AtomicReference<Payment> savedPayment = new AtomicReference<>();
        prepareSuccessfulCheckout(savedMapping, savedOrder, savedPayment);

        CheckoutRequest cart = request(item(productId, 2));
        CheckoutResponse first = service.checkout("checkout-replay", cart, userId);
        CheckoutResponse replay = service.checkout("checkout-replay", cart, userId);

        assertEquals(first.order().referenceId(), replay.order().referenceId());
        assertTrue(replay.replayed());
        verify(stock, times(1)).reserveStock(productId, 2);
        verify(orders, times(1)).saveAndFlush(any(PurchaseOrder.class));
        verify(payments, times(1)).save(any(Payment.class));
    }

    @Test
    void rejectsReusingIdempotencyKeyForDifferentCart() {
        AtomicReference<CheckoutIdempotency> savedMapping = new AtomicReference<>();
        AtomicReference<PurchaseOrder> savedOrder = new AtomicReference<>();
        AtomicReference<Payment> savedPayment = new AtomicReference<>();
        prepareSuccessfulCheckout(savedMapping, savedOrder, savedPayment);
        service.checkout("checkout-reused", request(item(productId, 2)), userId);

        CheckoutException exception = assertThrows(CheckoutException.class,
                () -> service.checkout("checkout-reused", request(item(UUID.randomUUID(), 1)), userId));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(stock, times(1)).reserveStock(productId, 2);
        verify(orders, times(1)).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    void failsCheckoutWhenAtomicStockReservationCannotBeMade() {
        stubAdvisoryLock();
        when(idempotencies.findById(new CheckoutIdempotencyId(userId, "out-of-stock"))).thenReturn(Optional.empty());
        when(products.findAllById(any())).thenReturn(List.of(activeProduct(productId, 250L)));
        when(stock.reserveStock(productId, 2)).thenReturn(0);

        CheckoutException exception = assertThrows(CheckoutException.class,
                () -> service.checkout("out-of-stock", request(item(productId, 2)), userId));

        assertEquals("OUT_OF_STOCK", exception.getCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(orders, never()).saveAndFlush(any(PurchaseOrder.class));
        verify(payments, never()).save(any(Payment.class));
        verify(idempotencies, never()).save(any(CheckoutIdempotency.class));
    }

    private void prepareSuccessfulCheckout() {
        prepareSuccessfulCheckout(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
    }

    private void prepareSuccessfulCheckout(AtomicReference<CheckoutIdempotency> savedMapping,
            AtomicReference<PurchaseOrder> savedOrder, AtomicReference<Payment> savedPayment) {
        stubAdvisoryLock();
        when(idempotencies.findById(any(CheckoutIdempotencyId.class)))
                .thenAnswer(invocation -> Optional.ofNullable(savedMapping.get()));
        when(idempotencies.save(any(CheckoutIdempotency.class))).thenAnswer(invocation -> {
            CheckoutIdempotency mapping = invocation.getArgument(0);
            savedMapping.set(mapping);
            return mapping;
        });
        when(products.findAllById(any())).thenReturn(List.of(activeProduct(productId, 250L)));
        when(stock.reserveStock(productId, 2)).thenReturn(1);
        when(orders.saveAndFlush(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            savedOrder.set(order);
            return order;
        });
        when(orders.findById(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(savedOrder.get()));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            savedPayment.set(payment);
            return payment;
        });
        when(payments.findByOrderId(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(savedPayment.get()));
    }

    private void stubAdvisoryLock() {
        doReturn(null).when(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    private Product activeProduct(UUID id, long pricePaise) {
        Product product = org.mockito.Mockito.mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getPricePaise()).thenReturn(pricePaise);
        when(product.isActive()).thenReturn(true);
        when(product.getSku()).thenReturn("SKU-TEST");
        when(product.getName()).thenReturn("Test product");
        return product;
    }

    private static CheckoutRequest request(CheckoutItemRequest... items) {
        return new CheckoutRequest(List.of(items));
    }

    private static CheckoutItemRequest item(UUID id, int quantity) {
        return new CheckoutItemRequest(id, quantity);
    }
}
