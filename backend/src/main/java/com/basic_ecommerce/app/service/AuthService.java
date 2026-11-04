package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.SessionResponse;
import com.basic_ecommerce.app.dto.SignupRequest;
import com.basic_ecommerce.app.model.AppUser;
import com.basic_ecommerce.app.model.AuthSession;
import com.basic_ecommerce.app.repository.AppUserRepository;
import com.basic_ecommerce.app.repository.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String DEMO_EMAIL = "demo@basicfit.local";
    private static final int SESSION_HOURS = 12;
    private final AppUserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository users, AuthSessionRepository sessions, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SessionResponse login(String email, String password) {
        AppUser user = users.findByEmail(normalizeEmail(email))
                .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
                .orElseThrow(() -> new AuthException("Email or password is incorrect.", HttpStatus.UNAUTHORIZED));
        return createSession(user);
    }

    @Transactional
    public SessionResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String displayName = request.displayName().trim();
        AppUser user = new AppUser(UUID.randomUUID(), email, displayName,
                passwordEncoder.encode(request.password()), Instant.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw new AuthException("An account with this email already exists.", HttpStatus.CONFLICT);
        }
        return createSession(user);
    }

    @Transactional
    public SessionResponse loginAsDemo() {
        AppUser demoUser = users.findByEmail(DEMO_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Demo account has not been seeded by Flyway."));
        return createSession(demoUser);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> userIdForValidToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hashToken(token), Instant.now())
                .map(AuthSession::getUserId);
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        sessions.findByTokenHashAndRevokedAtIsNull(hashToken(token)).ifPresent(session -> session.revoke(Instant.now()));
    }

    private SessionResponse createSession(AppUser user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SESSION_HOURS, ChronoUnit.HOURS);
        sessions.save(new AuthSession(hashToken(token), user.getId(), expiresAt, now));
        return new SessionResponse(token, "Bearer", expiresAt,
                new SessionResponse.User(user.getEmail(), user.getDisplayName()));
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(); }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
