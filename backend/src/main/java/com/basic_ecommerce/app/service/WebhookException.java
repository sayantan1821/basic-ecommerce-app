package com.basic_ecommerce.app.service;

import org.springframework.http.HttpStatus;

public class WebhookException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public WebhookException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
