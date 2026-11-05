package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.WebhookResult;
import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.OrderItemRepository;
import com.basic_ecommerce.app.repository.OrderProductStockRepository;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeWebhookService {
    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookService.class);
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PaymentRepository payments;
    private final PurchaseOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderProductStockRepository stock;

    public StripeWebhookService(
            @Value("${STRIPE_WEBHOOK_SECRET:}") String webhookSecret,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            PaymentRepository payments,
            PurchaseOrderRepository orders,
            OrderItemRepository orderItems,
            OrderProductStockRepository stock) {
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.payments = payments;
        this.orders = orders;
        this.orderItems = orderItems;
        this.stock = stock;
    }

    @Transactional
    public WebhookResult handleSignedWebhook(String signature, byte[] rawBody) {
        if (webhookSecret.isBlank()) {
            logger.error("Stripe webhook rejected because STRIPE_WEBHOOK_SECRET is not configured.");
            throw new WebhookException(HttpStatus.SERVICE_UNAVAILABLE, "STRIPE_WEBHOOK_NOT_CONFIGURED", "Set STRIPE_WEBHOOK_SECRET to receive Stripe events.");
        }
        if (signature == null || signature.isBlank() || rawBody == null) {
            logger.warn("Stripe webhook rejected because signature or request body is missing: signaturePresent={}, payloadPresent={}",
                    signature != null && !signature.isBlank(), rawBody != null);
            throw new WebhookException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Stripe-Signature header and raw event body are required.");
        }

        final Event verifiedEvent;
        try {
            verifiedEvent = Webhook.constructEvent(new String(rawBody, StandardCharsets.UTF_8), signature, webhookSecret);
        } catch (SignatureVerificationException exception) {
            logger.warn("Stripe webhook signature verification failed: payloadBytes={}", rawBody.length);
            throw new WebhookException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Stripe signature did not match the event body.");
        } catch (RuntimeException exception) {
            logger.warn("Stripe webhook envelope verification/parsing failed: exceptionType={}, payloadBytes={}",
                    exception.getClass().getSimpleName(), rawBody.length);
            throw new WebhookException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Stripe event body must be valid JSON.");
        }

        String eventId = verifiedEvent.getId();
        String eventType = verifiedEvent.getType();
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            logger.warn("Verified Stripe webhook could not be decoded as JSON: eventId={}, eventType={}, exceptionType={}",
                    eventId, eventType, exception.getClass().getSimpleName());
            throw new WebhookException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Stripe event body must be valid JSON.");
        }
        logger.info("Stripe webhook signature verified: eventId={}, eventType={}, livemode={}, payloadBytes={}",
                eventId, eventType, verifiedEvent.getLivemode(), rawBody.length);
        JsonNode session = root.path("data").path("object");
        Instant now = Instant.now();
        int inserted = jdbcTemplate.update(
                "INSERT INTO webhook_events (provider_event_id, event_type, payload_hash, received_at, result) "
                        + "VALUES (?, ?, ?, ?, 'IGNORED') ON CONFLICT (provider_event_id) DO NOTHING",
                eventId, eventType, sha256Hex(rawBody), Timestamp.from(now));
        if (inserted == 0) {
            logger.info("Ignored duplicate Stripe webhook: eventId={}, eventType={}", eventId, eventType);
            return new WebhookResult(eventId, "DUPLICATE", true, null, null);
        }

        if (!isCheckoutEvent(eventType)) {
            markEvent(eventId, "IGNORED", now);
            logger.info("Ignored unsupported Stripe event: eventId={}, eventType={}", eventId, eventType);
            return new WebhookResult(eventId, "IGNORED", false, null, null);
        }

        UUID paymentId;
        String paymentIdMetadata = session.path("metadata").path("paymentId").asText("");
        try {
            paymentId = UUID.fromString(paymentIdMetadata);
        } catch (RuntimeException exception) {
            logger.warn("Rejected Stripe Checkout webhook with missing or invalid paymentId metadata: eventId={}, eventType={}, metadataPresent={}",
                    eventId, eventType, !paymentIdMetadata.isBlank());
            markEvent(eventId, "REJECTED", now);
            return new WebhookResult(eventId, "REJECTED", false, null, null);
        }
        var maybePayment = payments.findByIdForUpdate(paymentId);
        if (maybePayment.isEmpty()) {
            logger.warn("Rejected Stripe Checkout webhook for unknown payment: eventId={}, eventType={}, paymentId={}", eventId, eventType, paymentId);
            markEvent(eventId, "REJECTED", now);
            return new WebhookResult(eventId, "REJECTED", false, null, null);
        }

        Payment payment = maybePayment.get();
        PurchaseOrder order = orders.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Payment has no order"));
        String sessionId = session.path("id").asText();
        String orderReference = session.path("metadata").path("orderReference").asText();
        long amountTotal = session.path("amount_total").asLong(-1);
        String currency = session.path("currency").asText("");
        boolean sessionIdPresent = !sessionId.isBlank();
        boolean storedSessionMatches = payment.getStripeCheckoutSessionId() == null || payment.getStripeCheckoutSessionId().equals(sessionId);
        boolean orderReferenceMatches = order.getReferenceId().equals(orderReference);
        boolean amountMatches = amountTotal == payment.getAmountPaise();
        boolean currencyMatches = "inr".equalsIgnoreCase(currency);
        if (!sessionIdPresent || !storedSessionMatches || !orderReferenceMatches || !amountMatches || !currencyMatches) {
            logger.warn("Rejected Stripe Checkout webhook due to metadata/session mismatch: eventId={}, eventType={}, paymentId={}, orderReference={}, sessionId={}, sessionIdPresent={}, storedSessionMatches={}, orderReferenceMatches={}, expectedAmountPaise={}, receivedAmountPaise={}, expectedCurrency=inr, receivedCurrency={}",
                    eventId, eventType, paymentId, order.getReferenceId(), sessionId, sessionIdPresent, storedSessionMatches,
                    orderReferenceMatches, payment.getAmountPaise(), amountTotal, currency);
            markEvent(eventId, "REJECTED", now);
            return new WebhookResult(eventId, "REJECTED", false, order.getStatus(), payment.getStatus());
        }

        if (!"PENDING".equals(payment.getStatus()) || !"PENDING_PAYMENT".equals(order.getStatus())) {
            markEvent(eventId, "IGNORED", now);
            logger.info("Ignored Stripe Checkout event because order/payment is already terminal: eventId={}, eventType={}, orderReference={}, orderStatus={}, paymentStatus={}",
                    eventId, eventType, order.getReferenceId(), order.getStatus(), payment.getStatus());
            return new WebhookResult(eventId, "IGNORED", false, order.getStatus(), payment.getStatus());
        }

        String paymentIntentId = scalarText(session.path("payment_intent"));
        payment.attachCheckoutSession(sessionId, payment.getStripeCheckoutUrl(), payment.getStripeCheckoutExpiresAt(), now);
        payment.attachPaymentIntent(paymentIntentId, now);

        if ("checkout.session.completed".equals(eventType) || "checkout.session.async_payment_succeeded".equals(eventType)) {
            if (!"paid".equalsIgnoreCase(session.path("payment_status").asText())) {
                logger.info("Stripe Checkout session is not paid yet: eventId={}, eventType={}, orderReference={}, paymentId={}, sessionId={}, stripePaymentStatus={}",
                        eventId, eventType, order.getReferenceId(), paymentId, sessionId, session.path("payment_status").asText());
                markEvent(eventId, "IGNORED", now);
                return new WebhookResult(eventId, "IGNORED", false, order.getStatus(), payment.getStatus());
            }
            payment.markSucceeded(now);
            order.confirm(now);
            logger.info("Stripe payment/order transitioned to success: eventId={}, orderReference={}, paymentId={}, sessionId={}, paymentIntentPresent={}, orderStatus={}, paymentStatus={}",
                    eventId, order.getReferenceId(), paymentId, sessionId, paymentIntentId != null, order.getStatus(), payment.getStatus());
        } else {
            payment.markFailed("checkout.session.expired".equals(eventType) ? "SESSION_EXPIRED" : "PAYMENT_FAILED", now);
            if ("checkout.session.expired".equals(eventType)) order.expire(now);
            else order.markPaymentFailed(now);
            var items = orderItems.findByOrderId(order.getId());
            items.forEach(item -> stock.releaseStock(item.getProductId(), item.getQuantity()));
            logger.info("Stripe payment/order transitioned to failure: eventId={}, eventType={}, orderReference={}, paymentId={}, sessionId={}, failureCode={}, releasedStockLines={}, orderStatus={}, paymentStatus={}",
                    eventId, eventType, order.getReferenceId(), paymentId, sessionId,
                    "checkout.session.expired".equals(eventType) ? "SESSION_EXPIRED" : "PAYMENT_FAILED",
                    items.size(), order.getStatus(), payment.getStatus());
        }

        markEvent(eventId, "PROCESSED", now);
        logger.info("Processed Stripe Checkout webhook: eventId={}, eventType={}, orderStatus={}, paymentStatus={}",
                eventId, eventType, order.getStatus(), payment.getStatus());
        return new WebhookResult(eventId, "PROCESSED", false, order.getStatus(), payment.getStatus());
    }

    private static boolean isCheckoutEvent(String type) {
        return "checkout.session.completed".equals(type)
                || "checkout.session.async_payment_succeeded".equals(type)
                || "checkout.session.async_payment_failed".equals(type)
                || "checkout.session.expired".equals(type);
    }

    private static String scalarText(JsonNode node) {
        return node == null || node.isNull() || !node.isValueNode() ? null : node.asText();
    }

    private void markEvent(String eventId, String result, Instant now) {
        int updated = jdbcTemplate.update("UPDATE webhook_events SET result = ?, processed_at = ? WHERE provider_event_id = ?",
                result, Timestamp.from(now), eventId);
        if (updated != 1) {
            logger.error("Stripe webhook event history update affected unexpected row count: eventId={}, result={}, rowsUpdated={}",
                    eventId, result, updated);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
