package com.tiendatech.usuarios.infrastructure.persistence.adapter;

import com.tiendatech.usuarios.domain.model.auth.RefreshSession;
import com.tiendatech.usuarios.domain.port.out.RefreshSessionRepository;
import com.tiendatech.usuarios.infrastructure.persistence.entity.auth.RefreshToken;
import com.tiendatech.usuarios.infrastructure.persistence.repository.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshSessionRepositoryAdapter implements RefreshSessionRepository {
    private final RefreshTokenRepository repository;

    @Override
    public RefreshSession save(RefreshSession session) {
        return toDomain(repository.save(toEntity(session)));
    }

    @Override
    public Optional<RefreshSession> findActive(UUID jti) {
        return repository.findByJtiAndRevokedFalse(jti).map(this::toDomain);
    }

    @Override
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId);
    }

    private RefreshSession toDomain(RefreshToken entity) {
        return new RefreshSession(entity.getJti(), entity.getUserId(), entity.getRole(), entity.getFamilyId(),
                entity.getIssuedAt(), entity.getLastSeen(), entity.getAbsoluteExp(), entity.isRevoked());
    }

    private RefreshToken toEntity(RefreshSession session) {
        RefreshToken entity = new RefreshToken();
        entity.setJti(session.jti());
        entity.setUserId(session.userId());
        entity.setRole(session.role());
        entity.setFamilyId(session.familyId());
        entity.setIssuedAt(session.issuedAt());
        entity.setLastSeen(session.lastSeen());
        entity.setAbsoluteExp(session.absoluteExpiration());
        entity.setRevoked(session.revoked());
        return entity;
    }
}
