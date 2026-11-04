package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.CheckoutRequest;
import com.basic_ecommerce.app.dto.CheckoutResponse;
import com.basic_ecommerce.app.service.CheckoutService;
import com.basic_ecommerce.app.security.AuthAttributes;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            @RequestAttribute(AuthAttributes.USER_ID) UUID userId) {
        CheckoutResponse response = checkoutService.checkout(idempotencyKey, request, userId);
        return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

}
