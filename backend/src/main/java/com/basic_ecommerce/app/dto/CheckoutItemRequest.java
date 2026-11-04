package com.basic_ecommerce.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckoutItemRequest(
        @NotNull UUID productId,
        @Min(1) @Max(10) int quantity) {}
