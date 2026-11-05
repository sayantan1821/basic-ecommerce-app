package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.WebhookResult;
import com.basic_ecommerce.app.service.StripeWebhookService;
import com.basic_ecommerce.app.service.WebhookException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class PaymentWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(PaymentWebhookController.class);
    private final StripeWebhookService webhookService;

    public PaymentWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebhookResult receiveStripeEvent(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {
        logger.info("Stripe webhook HTTP request received: signaturePresent={}, payloadBytes={}",
                signature != null && !signature.isBlank(), rawBody == null ? 0 : rawBody.length);
        try {
            WebhookResult result = webhookService.handleSignedWebhook(signature, rawBody);
            logger.info("Stripe webhook HTTP request completed: eventId={}, result={}, duplicate={}, orderStatus={}, paymentStatus={}",
                    result.eventId(), result.result(), result.duplicate(), result.orderStatus(), result.paymentStatus());
            return result;
        } catch (WebhookException exception) {
            logger.warn("Stripe webhook HTTP request rejected: errorCode={}, status={}",
                    exception.getCode(), exception.getStatus().value());
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("Stripe webhook processing failed unexpectedly: exceptionType={}",
                    exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }
}
