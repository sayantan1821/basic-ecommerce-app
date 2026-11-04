package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.LoginRequest;
import com.basic_ecommerce.app.dto.SessionResponse;
import com.basic_ecommerce.app.dto.SignupRequest;
import com.basic_ecommerce.app.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/signup")
    public SessionResponse signup(@Valid @RequestBody SignupRequest request) {
        return auth.signup(request);
    }

    @PostMapping("/demo")
    public SessionResponse demo() {
        return auth.loginAsDemo();
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        auth.revoke(bearerToken(authorization));
    }

    private static String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
    }
}
