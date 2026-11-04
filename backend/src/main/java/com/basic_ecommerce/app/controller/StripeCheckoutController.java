package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.StripeCheckoutResponse;
import com.basic_ecommerce.app.service.StripeCheckoutService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class StripeCheckoutController {
    private final StripeCheckoutService stripeCheckoutService;

    public StripeCheckoutController(StripeCheckoutService stripeCheckoutService) {
        this.stripeCheckoutService = stripeCheckoutService;
    }

    @PostMapping("/{paymentId}/stripe-session")
    public StripeCheckoutResponse createSession(@PathVariable UUID paymentId) {
        return stripeCheckoutService.createSession(paymentId);
    }
}
