package com.basic_ecommerce.app.dto;

public record StripeCheckoutResponse(String sessionId, String checkoutUrl) {}
