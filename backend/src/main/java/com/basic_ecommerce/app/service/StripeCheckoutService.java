package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.StripeCheckoutResponse;
import com.basic_ecommerce.app.model.OrderItem;
import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.OrderItemRepository;
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

    public StripeCheckoutService(
            @Value("${STRIPE_SECRET_KEY:}") String stripeSecretKey,
            @Value("${FRONTEND_BASE_URL:http://localhost:8080}") String frontendBaseUrl,
            PaymentRepository payments,
            PurchaseOrderRepository orders,
            OrderItemRepository orderItems) {
        this.stripeSecretKey = stripeSecretKey;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
        this.payments = payments;
        this.orders = orders;
        this.orderItems = orderItems;
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

        if (!"PENDING".equals(payment.getStatus()) || !"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new CheckoutException("PAYMENT_NOT_PENDING", "This order is no longer awaiting payment.", HttpStatus.CONFLICT);
        }
        if (payment.getStripeCheckoutUrl() != null && !payment.getStripeCheckoutUrl().isBlank()
                && payment.getStripeCheckoutExpiresAt() != null && payment.getStripeCheckoutExpiresAt().isAfter(Instant.now())) {
            return new StripeCheckoutResponse(payment.getStripeCheckoutSessionId(), payment.getStripeCheckoutUrl());
        }

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
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
            return new StripeCheckoutResponse(session.getId(), session.getUrl());
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

    private static String redactSecrets(String message) {
        return message
                .replaceAll("(?i)(sk|rk)_(test|live)_[A-Za-z0-9_*\\[\\]-]+", "$1_$2_[REDACTED]")
                .replaceAll("(?i)whsec_[A-Za-z0-9_*\\[\\]-]+", "whsec_[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]")
                .replaceAll("[\\r\\n]+", " ");
    }
}
