package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.auth.RefreshSession;

import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository {
    RefreshSession save(RefreshSession session);
    Optional<RefreshSession> findActive(UUID jti);
    void revokeFamily(UUID familyId);
}
