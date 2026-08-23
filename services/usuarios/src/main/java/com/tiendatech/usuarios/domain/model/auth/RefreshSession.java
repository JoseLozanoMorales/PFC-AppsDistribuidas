package com.tiendatech.usuarios.domain.model.auth;

import java.time.Instant;
import java.util.UUID;

/** Sesion renovable del dominio, sin dependencias de JPA o Spring. */
public record RefreshSession(
        UUID jti,
        Integer userId,
        String role,
        UUID familyId,
        Instant issuedAt,
        Instant lastSeen,
        Instant absoluteExpiration,
        boolean revoked
) {
    public RefreshSession revoke() {
        return new RefreshSession(jti, userId, role, familyId, issuedAt, lastSeen, absoluteExpiration, true);
    }
}
