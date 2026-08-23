package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.auth.RefreshClaims;

import java.time.Instant;
import java.util.UUID;

public interface TokenPort {
    String generateAccess(Integer userId, String username, String role, int minutes);
    String generateRefresh(Integer userId, String role, UUID jti, UUID familyId, Instant absoluteExpiration);
    RefreshClaims parseRefresh(String token);
}
