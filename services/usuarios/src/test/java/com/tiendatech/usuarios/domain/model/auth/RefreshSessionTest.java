package com.tiendatech.usuarios.domain.model.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshSessionTest {
    @Test
    void revokeReturnsRevokedCopyWithoutChangingIdentityOrExpiration() {
        UUID jti = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(3600);
        RefreshSession active = new RefreshSession(jti, 7, "CLIENTE", family, now, now, expiration, false);

        RefreshSession revoked = active.revoke();

        assertFalse(active.revoked());
        assertTrue(revoked.revoked());
        assertEquals(active.jti(), revoked.jti());
        assertEquals(active.familyId(), revoked.familyId());
        assertEquals(active.absoluteExpiration(), revoked.absoluteExpiration());
    }
}
