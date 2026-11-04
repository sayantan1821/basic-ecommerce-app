package com.basic_ecommerce.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoBearerTokenFilter extends OncePerRequestFilter {
    private final byte[] expectedToken;

    public DemoBearerTokenFilter(@Value("${DEMO_API_TOKEN:local-demo-token}") String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean checkout = "POST".equalsIgnoreCase(request.getMethod()) && "/api/checkout".equals(path);
        boolean stripeSession = "POST".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/payments/[0-9a-fA-F-]{36}/stripe-session");
        boolean orderStatus = "GET".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/orders/by-reference/[0-9a-fA-F-]{36}");
        return !(checkout || stripeSession || orderStatus);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : "";
        if (!MessageDigest.isEqual(expectedToken, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"A valid demo bearer token is required.\"}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
