package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.service.CheckoutException;
import com.basic_ecommerce.app.service.AuthException;
import com.basic_ecommerce.app.service.WebhookException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> handleAuth(AuthException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error("AUTHENTICATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(WebhookException.class)
    public ResponseEntity<?> handleWebhook(WebhookException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(CheckoutException.class)
    public ResponseEntity<?> handleCheckout(CheckoutException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<?> handleMalformedRequest(Exception exception) {
        String message = exception instanceof MissingRequestHeaderException missing
                ? "The " + missing.getHeaderName() + " header is required."
                : "Request body must be valid JSON.";
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "The request could not be completed."));
    }

    private static Map<String, Object> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message));
    }
}
