package com.basic_ecommerce.app.dto;

public record WebhookResult(
        String eventId,
        String result,
        boolean duplicate,
        String orderStatus,
        String paymentStatus) {}
