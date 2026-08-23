package com.tiendatech.usuarios.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {
    private final JwtUtil jwt = new JwtUtil(
            "test-secret-that-is-long-enough-for-hmac-sha-256-signatures-123456789"
    );

    @Test
    void parsesGeneratedAccessToken() {
        String token = jwt.generateAccess(8, "cliente8", "CLIENTE", 15);

        var claims = jwt.parseAccess(token);

        assertEquals(8, claims.userId());
        assertEquals("cliente8", claims.username());
        assertEquals("CLIENTE", claims.role());
    }

    @Test
    void doesNotAcceptRefreshTokenAsBearerAccessToken() {
        String refresh = jwt.generateRefresh(
                8,
                "CLIENTE",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now().plusSeconds(3600)
        );

        assertThrows(IllegalArgumentException.class, () -> jwt.parseAccess(refresh));
    }
}
