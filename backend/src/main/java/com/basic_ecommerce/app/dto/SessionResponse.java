package com.basic_ecommerce.app.dto;

import java.time.Instant;

public record SessionResponse(String token, String tokenType, Instant expiresAt, User user) {
    public record User(String email, String displayName) {}
}
