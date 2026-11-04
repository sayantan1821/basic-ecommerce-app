package com.basic_ecommerce.app.dto;

public record OrderStatusResponse(String referenceId, String orderStatus, String paymentStatus, String currency, long totalPaise) {}
