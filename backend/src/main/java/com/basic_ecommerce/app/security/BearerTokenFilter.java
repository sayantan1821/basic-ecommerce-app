package com.basic_ecommerce.app.security;

import com.basic_ecommerce.app.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private final AuthService auth;

    public BearerTokenFilter(AuthService auth) { this.auth = auth; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!path.startsWith("/api/")) return true;
        return "/api/health".equals(path)
                || ("GET".equalsIgnoreCase(request.getMethod()) && "/api/products".equals(path))
                || "/api/auth/login".equals(path)
                || "/api/auth/signup".equals(path)
                || "/api/auth/demo".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        var userId = auth.userIdForValidToken(token);
        if (userId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Sign in with a valid session to continue.\"}}");
            return;
        }
        request.setAttribute(AuthAttributes.USER_ID, userId.get());
        chain.doFilter(request, response);
    }
}
