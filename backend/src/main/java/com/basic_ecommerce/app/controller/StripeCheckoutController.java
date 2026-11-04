package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.StripeCheckoutResponse;
import com.basic_ecommerce.app.service.StripeCheckoutService;
import com.basic_ecommerce.app.security.AuthAttributes;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestAttribute;
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
    public StripeCheckoutResponse createSession(@PathVariable UUID paymentId,
            @RequestAttribute(AuthAttributes.USER_ID) UUID userId) {
        return stripeCheckoutService.createSession(paymentId, userId);
    }
}
