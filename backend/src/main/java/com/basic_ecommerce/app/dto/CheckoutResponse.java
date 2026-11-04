package com.basic_ecommerce.app.dto;

import java.util.UUID;

public record CheckoutResponse(OrderSummary order, PaymentSummary payment, boolean replayed) {
    public record OrderSummary(String referenceId, String status, String currency, long totalPaise) {}
    public record PaymentSummary(UUID id, String status) {}
}
