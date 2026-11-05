package com.basic_ecommerce.app.dto;

import java.time.Instant;

public record OrderStatusResponse(String referenceId, String orderStatus, String paymentStatus, String currency, long totalPaise, Instant paidAt) {}
