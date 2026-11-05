package com.basic_ecommerce.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.basic_ecommerce.app.dto.WebhookResult;
import com.basic_ecommerce.app.model.OrderItem;
import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.OrderItemRepository;
import com.basic_ecommerce.app.repository.OrderProductStockRepository;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeWebhookServiceTest {
    private static final String SIGNING_KEY = "unit-test-stripe-webhook-secret";
    private static final String WEBHOOK_SECRET = "whsec_" + SIGNING_KEY;

    private JdbcTemplate jdbcTemplate;
    private PaymentRepository payments;
    private PurchaseOrderRepository orders;
    private OrderItemRepository orderItems;
    private OrderProductStockRepository stock;
    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        payments = org.mockito.Mockito.mock(PaymentRepository.class);
        orders = org.mockito.Mockito.mock(PurchaseOrderRepository.class);
        orderItems = org.mockito.Mockito.mock(OrderItemRepository.class);
        stock = org.mockito.Mockito.mock(OrderProductStockRepository.class);
        service = new StripeWebhookService(WEBHOOK_SECRET, new ObjectMapper(), jdbcTemplate,
                payments, orders, orderItems, stock);
    }

    @Test
    void rejectsInvalidSignatureBeforeRecordingOrProcessingEvent() throws Exception {
        String payload = eventPayload("evt_bad_signature", "customer.created", "{\"id\":\"cus_test\",\"object\":\"customer\"}");
        String invalidHeader = sign(payload).replaceAll("v1=[0-9a-f]+", "v1=" + "0".repeat(64));

        WebhookException exception = assertThrows(WebhookException.class,
                () -> service.handleSignedWebhook(invalidHeader, payload.getBytes(StandardCharsets.UTF_8)));

        assertEquals("INVALID_SIGNATURE", exception.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verifyNoInteractions(jdbcTemplate, payments, orders, orderItems, stock);
    }

    @Test
    void returnsDuplicateWithoutReapplyingAPreviouslyRecordedStripeEvent() throws Exception {
        String payload = eventPayload("evt_duplicate", "customer.created", "{\"id\":\"cus_test\",\"object\":\"customer\"}");
        when(jdbcTemplate.update(startsWith("INSERT INTO webhook_events"), any(), any(), any(), any())).thenReturn(0);

        WebhookResult result = service.handleSignedWebhook(sign(payload), payload.getBytes(StandardCharsets.UTF_8));

        assertEquals("evt_duplicate", result.eventId());
        assertEquals("DUPLICATE", result.result());
        assertEquals(true, result.duplicate());
        verify(jdbcTemplate).update(startsWith("INSERT INTO webhook_events"), any(), any(), any(), any());
        verify(jdbcTemplate, never()).update(startsWith("UPDATE webhook_events"), any(), any(), any());
        verifyNoInteractions(payments, orders, orderItems, stock);
    }

    @Test
    void verifiedPaidCheckoutEventConfirmsOrderAndRecordsPaymentTime() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Payment payment = new Payment(paymentId, orderId, "pending_ref", 1250L, createdAt);
        PurchaseOrder order = new PurchaseOrder(orderId, "order-ref-123", 1250L, UUID.randomUUID(), createdAt);
        when(jdbcTemplate.update(startsWith("INSERT INTO webhook_events"), any(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.update(startsWith("UPDATE webhook_events"), any(), any(), any())).thenReturn(1);
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        String session = checkoutSession("cs_test_paid", paymentId, order.getReferenceId(), 1250L,
                "inr", "paid", "pi_test_paid");
        String payload = eventPayload("evt_paid", "checkout.session.completed", session);

        WebhookResult result = service.handleSignedWebhook(sign(payload), payload.getBytes(StandardCharsets.UTF_8));

        assertEquals("PROCESSED", result.result());
        assertEquals("CONFIRMED", result.orderStatus());
        assertEquals("SUCCEEDED", result.paymentStatus());
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals("SUCCEEDED", payment.getStatus());
        assertNotNull(payment.getPaidAt());
        assertEquals("cs_test_paid", payment.getStripeCheckoutSessionId());
        assertEquals("pi_test_paid", payment.getStripePaymentIntentId());
        verify(jdbcTemplate).update(startsWith("UPDATE webhook_events"), any(), any(), any());
    }

    @Test
    void expiredCheckoutFailsPaymentAndReleasesReservedStock() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Payment payment = new Payment(paymentId, orderId, "pending_ref", 1250L, createdAt);
        PurchaseOrder order = new PurchaseOrder(orderId, "order-ref-expired", 1250L, UUID.randomUUID(), createdAt);
        OrderItem orderItem = org.mockito.Mockito.mock(OrderItem.class);
        when(orderItem.getProductId()).thenReturn(productId);
        when(orderItem.getQuantity()).thenReturn(2);
        when(jdbcTemplate.update(startsWith("INSERT INTO webhook_events"), any(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.update(startsWith("UPDATE webhook_events"), any(), any(), any())).thenReturn(1);
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        String session = checkoutSession("cs_test_expired", paymentId, order.getReferenceId(), 1250L,
                "inr", "unpaid", null);
        String payload = eventPayload("evt_expired", "checkout.session.expired", session);

        WebhookResult result = service.handleSignedWebhook(sign(payload), payload.getBytes(StandardCharsets.UTF_8));

        assertEquals("PROCESSED", result.result());
        assertEquals("EXPIRED", result.orderStatus());
        assertEquals("FAILED", result.paymentStatus());
        assertEquals("EXPIRED", order.getStatus());
        assertEquals("SESSION_EXPIRED", payment.getFailureCode());
        verify(stock).releaseStock(productId, 2);
    }

    private static String checkoutSession(String sessionId, UUID paymentId, String orderReference,
            long amount, String currency, String paymentStatus, String paymentIntent) {
        String paymentIntentJson = paymentIntent == null ? "null" : "\"" + paymentIntent + "\"";
        return "{\"id\":\"" + sessionId + "\",\"object\":\"checkout.session\","
                + "\"metadata\":{\"paymentId\":\"" + paymentId + "\",\"orderReference\":\""
                + orderReference + "\"},\"amount_total\":" + amount + ",\"currency\":\"" + currency
                + "\",\"payment_status\":\"" + paymentStatus + "\",\"payment_intent\":" + paymentIntentJson + "}";
    }

    private static String eventPayload(String eventId, String eventType, String dataObject) {
        String created = Long.toString(Instant.now().getEpochSecond());
        return "{\"id\":\"" + eventId + "\",\"object\":\"event\",\"api_version\":\"2025-03-31.basil\","
                + "\"created\":" + created + ",\"data\":{\"object\":" + dataObject + "},"
                + "\"livemode\":false,\"pending_webhooks\":1,\"type\":\"" + eventType + "\"}";
    }

    private static String sign(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
