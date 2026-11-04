package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.AuthSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    Optional<AuthSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);
    Optional<AuthSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
