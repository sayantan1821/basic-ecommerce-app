package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.StripeCheckoutResponse;
import com.basic_ecommerce.app.dto.OrderStatusResponse;
import com.basic_ecommerce.app.model.OrderItem;
import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.OrderItemRepository;
import com.basic_ecommerce.app.repository.OrderProductStockRepository;
import com.basic_ecommerce.app.repository.AppUserRepository;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeCheckoutService {
    private static final Logger logger = LoggerFactory.getLogger(StripeCheckoutService.class);
    private final String stripeSecretKey;
    private final String frontendBaseUrl;
    private final PaymentRepository payments;
    private final PurchaseOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderProductStockRepository stock;
    private final AppUserRepository users;

    public StripeCheckoutService(
            @Value("${STRIPE_SECRET_KEY:}") String stripeSecretKey,
            @Value("${FRONTEND_BASE_URL:http://localhost:8080}") String frontendBaseUrl,
            PaymentRepository payments,
            PurchaseOrderRepository orders,
            OrderItemRepository orderItems,
            OrderProductStockRepository stock,
            AppUserRepository users) {
        this.stripeSecretKey = stripeSecretKey;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
        this.payments = payments;
        this.orders = orders;
        this.orderItems = orderItems;
        this.stock = stock;
        this.users = users;
    }

    @Transactional
    public StripeCheckoutResponse createSession(UUID paymentId, UUID userId) {
        if (!stripeSecretKey.startsWith("sk_test_") && !stripeSecretKey.startsWith("rk_test_")) {
            throw new CheckoutException("STRIPE_TEST_KEY_REQUIRED", "Set STRIPE_SECRET_KEY to a Stripe test-mode secret key.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CheckoutException("PAYMENT_NOT_FOUND", "Payment was not found.", HttpStatus.NOT_FOUND));
        PurchaseOrder order = orders.findByIdAndUserId(payment.getOrderId(), userId)
                .orElseThrow(() -> new CheckoutException("PAYMENT_NOT_FOUND", "Payment was not found.", HttpStatus.NOT_FOUND));
        var user = users.findById(userId)
                .orElseThrow(() -> new CheckoutException("USER_NOT_FOUND", "User was not found.", HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(payment.getStatus()) || !"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new CheckoutException("PAYMENT_NOT_PENDING", "This order is no longer awaiting payment.", HttpStatus.CONFLICT);
        }
        StripeCheckoutResponse existingSession = getExistingSessionResponse(payment, order);
        if (existingSession != null) {
            return existingSession;
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(user.getEmail())
                .setClientReferenceId(payment.getId().toString())
                .setSuccessUrl(frontendBaseUrl + "/?stripe=success&order=" + order.getReferenceId() + "&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendBaseUrl + "/?stripe=cancelled&order=" + order.getReferenceId())
                .putMetadata("paymentId", payment.getId().toString())
                .putMetadata("orderReference", order.getReferenceId())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("paymentId", payment.getId().toString())
                        .putMetadata("orderReference", order.getReferenceId())
                        .build());

        for (OrderItem item : orderItems.findByOrderId(order.getId())) {
            SessionCreateParams.LineItem.PriceData.ProductData productData = SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName(item.getProductNameSnapshot())
                    .build();
            SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("inr")
                    .setUnitAmount(item.getUnitPricePaise())
                    .setProductData(productData)
                    .build();
            params.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) item.getQuantity())
                    .setPriceData(priceData)
                    .build());
        }

        try {
            RequestOptions options = RequestOptions.builder()
                    .setApiKey(stripeSecretKey)
                    .setIdempotencyKey("basic-ecommerce-checkout-" + payment.getId()
                            + (payment.getStripeCheckoutSessionId() == null ? "-initial" : "-" + UUID.randomUUID()))
                    .build();
            Session session = Session.create(params.build(), options);
            if (session.getUrl() == null || session.getUrl().isBlank()) {
                throw new CheckoutException("STRIPE_SESSION_INVALID", "Stripe did not return a Checkout URL.", HttpStatus.BAD_GATEWAY);
            }
            Instant expiresAt = session.getExpiresAt() == null ? Instant.now().plusSeconds(86400) : Instant.ofEpochSecond(session.getExpiresAt());
            payment.attachCheckoutSession(session.getId(), session.getUrl(), expiresAt, Instant.now());
            payments.save(payment);
            return new StripeCheckoutResponse(session.getId(), session.getUrl(), false);
        } catch (StripeException exception) {
            StripeError stripeError = exception.getStripeError();
            String stripeMessage = stripeError == null || stripeError.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : redactSecrets(stripeError.getMessage());
            logger.warn("Stripe Checkout Session creation failed: exceptionType={}, statusCode={}, code={}, requestId={}, message={}",
                    exception.getClass().getSimpleName(), exception.getStatusCode(), exception.getCode(), exception.getRequestId(), stripeMessage);
            throw new CheckoutException("STRIPE_SESSION_FAILED", "Stripe could not start checkout. You can safely retry.", HttpStatus.BAD_GATEWAY);
        }
    }

    private StripeCheckoutResponse getExistingSessionResponse(Payment payment, PurchaseOrder order) {
        String sessionId = payment.getStripeCheckoutSessionId();
        if (sessionId == null || sessionId.isBlank()) return null;

        Session session;
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(stripeSecretKey).build();
            session = Session.retrieve(sessionId, options);
        } catch (StripeException exception) {
            StripeError stripeError = exception.getStripeError();
            String stripeMessage = stripeError == null || stripeError.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : redactSecrets(stripeError.getMessage());
            logger.warn("Stripe Checkout Session lookup failed: paymentId={}, sessionId={}, exceptionType={}, statusCode={}, code={}, requestId={}, message={}",
                    payment.getId(), sessionId, exception.getClass().getSimpleName(), exception.getStatusCode(),
                    exception.getCode(), exception.getRequestId(), stripeMessage);
            throw new CheckoutException("STRIPE_SESSION_LOOKUP_FAILED", "Could not verify your existing Stripe checkout. Retry in a moment.", HttpStatus.BAD_GATEWAY);
        }

        String sessionStatus = session.getStatus();
        String stripePaymentStatus = session.getPaymentStatus();
        Instant expiresAt = session.getExpiresAt() == null ? null : Instant.ofEpochSecond(session.getExpiresAt());
        Instant now = Instant.now();

        if ("open".equalsIgnoreCase(sessionStatus)
                && session.getUrl() != null && !session.getUrl().isBlank()
                && (expiresAt == null || expiresAt.isAfter(now))) {
            logger.info("Reusing open Stripe Checkout Session: paymentId={}, orderReference={}, sessionId={}, expiresAt={}",
                    payment.getId(), order.getReferenceId(), sessionId, expiresAt);
            return new StripeCheckoutResponse(session.getId(), session.getUrl(), false);
        }

        if ("complete".equalsIgnoreCase(sessionStatus)) {
            if ("paid".equalsIgnoreCase(stripePaymentStatus)) {
                if (!matchesPayment(session, payment, order)) {
                    logger.error("Stripe Checkout Session lookup found a paid Session that did not match the order: paymentId={}, orderReference={}, sessionId={}",
                            payment.getId(), order.getReferenceId(), sessionId);
                    throw new CheckoutException("STRIPE_SESSION_MISMATCH", "Stripe returned a payment that does not match this order.", HttpStatus.CONFLICT);
                }
                payment.attachCheckoutSession(session.getId(), session.getUrl(), expiresAt, now);
                payment.attachPaymentIntent(session.getPaymentIntent(), now);
                payment.markSucceeded(now);
                order.confirm(now);
                payments.save(payment);
                logger.warn("Recovered a paid Stripe Checkout Session while creating checkout: paymentId={}, orderReference={}, sessionId={}, orderStatus={}, paymentStatus={}",
                        payment.getId(), order.getReferenceId(), sessionId, order.getStatus(), payment.getStatus());
                return new StripeCheckoutResponse(session.getId(), null, true);
            }

            logger.warn("Stripe Checkout Session is complete but payment is not paid: paymentId={}, orderReference={}, sessionId={}, stripePaymentStatus={}",
                    payment.getId(), order.getReferenceId(), sessionId, stripePaymentStatus);
            throw new CheckoutException("STRIPE_SESSION_COMPLETE_UNPAID",
                    "This checkout has finished but Stripe has not confirmed payment. Check your order status before retrying.",
                    HttpStatus.CONFLICT);
        }

        if ("expired".equalsIgnoreCase(sessionStatus) || (expiresAt != null && !expiresAt.isAfter(now))) {
            logger.info("Stored Stripe Checkout Session has expired; creating a fresh session: paymentId={}, orderReference={}, sessionId={}",
                    payment.getId(), order.getReferenceId(), sessionId);
            return null;
        }

        logger.warn("Stripe Checkout Session has an unexpected state: paymentId={}, orderReference={}, sessionId={}, sessionStatus={}, stripePaymentStatus={}",
                payment.getId(), order.getReferenceId(), sessionId, sessionStatus, stripePaymentStatus);
        throw new CheckoutException("STRIPE_SESSION_STATE_UNKNOWN",
                "Could not determine the status of your existing Stripe checkout. Check your order status before retrying.",
                HttpStatus.CONFLICT);
    }

    @Transactional
    public OrderStatusResponse reconcileByReference(String referenceId, UUID userId) {
        PurchaseOrder order = orders.findByReferenceIdAndUserId(referenceId, userId)
                .orElseThrow(() -> new CheckoutException("ORDER_NOT_FOUND", "Order was not found.", HttpStatus.NOT_FOUND));
        Payment payment = payments.findByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new IllegalStateException("Order has no payment"));

        if (!"PENDING".equals(payment.getStatus()) || !"PENDING_PAYMENT".equals(order.getStatus())
                || payment.getStripeCheckoutSessionId() == null || payment.getStripeCheckoutSessionId().isBlank()) {
            return orderStatus(order, payment);
        }
        if (!stripeSecretKey.startsWith("sk_test_") && !stripeSecretKey.startsWith("rk_test_")) {
            throw new CheckoutException("STRIPE_TEST_KEY_REQUIRED", "Set STRIPE_SECRET_KEY to a Stripe test-mode secret key.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        Session session;
        try {
            session = Session.retrieve(payment.getStripeCheckoutSessionId(),
                    RequestOptions.builder().setApiKey(stripeSecretKey).build());
        } catch (StripeException exception) {
            StripeError stripeError = exception.getStripeError();
            String stripeMessage = stripeError == null || stripeError.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : redactSecrets(stripeError.getMessage());
            logger.warn("Stripe Checkout Session reconciliation failed: paymentId={}, orderReference={}, sessionId={}, exceptionType={}, statusCode={}, code={}, requestId={}, message={}",
                    payment.getId(), order.getReferenceId(), payment.getStripeCheckoutSessionId(),
                    exception.getClass().getSimpleName(), exception.getStatusCode(), exception.getCode(), exception.getRequestId(), stripeMessage);
            throw new CheckoutException("STRIPE_SESSION_LOOKUP_FAILED", "Could not verify payment with Stripe. Retry in a moment.", HttpStatus.BAD_GATEWAY);
        }

        if ("expired".equalsIgnoreCase(session.getStatus()) && !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            Instant now = Instant.now();
            payment.markFailed("SESSION_EXPIRED", now);
            order.expire(now);
            var items = orderItems.findByOrderId(order.getId());
            items.forEach(item -> stock.releaseStock(item.getProductId(), item.getQuantity()));
            payments.save(payment);
            logger.info("Expired Stripe Checkout Session reconciled on explicit status refresh: paymentId={}, orderReference={}, sessionId={}, releasedStockLines={}",
                    payment.getId(), order.getReferenceId(), session.getId(), items.size());
            return orderStatus(order, payment);
        }

        if (!"complete".equalsIgnoreCase(session.getStatus()) || !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            logger.info("Stripe reconciliation found no completed payment: paymentId={}, orderReference={}, sessionId={}, sessionStatus={}, stripePaymentStatus={}",
                    payment.getId(), order.getReferenceId(), session.getId(), session.getStatus(), session.getPaymentStatus());
            return orderStatus(order, payment);
        }

        if (!matchesPayment(session, payment, order)) {
            logger.error("Stripe reconciliation rejected a paid Session because it did not match the order: paymentId={}, orderReference={}, sessionId={}",
                    payment.getId(), order.getReferenceId(), session.getId());
            throw new CheckoutException("STRIPE_SESSION_MISMATCH", "Stripe returned a payment that does not match this order.", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        payment.attachPaymentIntent(session.getPaymentIntent(), now);
        payment.markSucceeded(now);
        order.confirm(now);
        payments.save(payment);
        logger.info("Reconciled paid Stripe Checkout Session on explicit status refresh: paymentId={}, orderReference={}, sessionId={}, paymentIntentPresent={}",
                payment.getId(), order.getReferenceId(), session.getId(), session.getPaymentIntent() != null);
        return orderStatus(order, payment);
    }

    private static OrderStatusResponse orderStatus(PurchaseOrder order, Payment payment) {
        return new OrderStatusResponse(order.getReferenceId(), order.getStatus(), payment.getStatus(),
                order.getCurrency(), order.getTotalPaise(), payment.getPaidAt());
    }

    private static boolean matchesPayment(Session session, Payment payment, PurchaseOrder order) {
        var metadata = session.getMetadata();
        return metadata != null
                && payment.getId().toString().equals(metadata.get("paymentId"))
                && order.getReferenceId().equals(metadata.get("orderReference"))
                && payment.getStripeCheckoutSessionId().equals(session.getId())
                && session.getAmountTotal() != null
                && session.getAmountTotal() == payment.getAmountPaise()
                && "inr".equalsIgnoreCase(session.getCurrency());
    }

    private static String redactSecrets(String message) {
        return message
                .replaceAll("(?i)(sk|rk)_(test|live)_[A-Za-z0-9_*\\[\\]-]+", "$1_$2_[REDACTED]")
                .replaceAll("(?i)whsec_[A-Za-z0-9_*\\[\\]-]+", "whsec_[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]")
                .replaceAll("[\\r\\n]+", " ");
    }
}
