CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auth_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions(expires_at);

-- Demo account credentials: demo@basicfit.local / demo12345
-- The BCrypt hash is seeded once through Flyway; the application never creates this user.
INSERT INTO app_users(id, email, display_name, password_hash)
VALUES ('00000000-0000-4000-8000-000000000099', 'demo@basicfit.local', 'Demo User', '$2a$10$JDUI1SawD.GQ3X9VhizkLOdOFrGUG7AzsJ1OxIFst2TbaLzUBDDIe');
