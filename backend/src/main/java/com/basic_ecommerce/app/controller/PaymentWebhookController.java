package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.WebhookResult;
import com.basic_ecommerce.app.service.StripeWebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class PaymentWebhookController {
    private final StripeWebhookService webhookService;

    public PaymentWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebhookResult receiveStripeEvent(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody byte[] rawBody) {
        return webhookService.handleSignedWebhook(signature, rawBody);
    }
}
