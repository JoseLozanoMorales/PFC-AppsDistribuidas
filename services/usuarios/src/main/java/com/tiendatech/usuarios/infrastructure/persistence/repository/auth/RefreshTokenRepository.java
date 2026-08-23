package com.tiendatech.usuarios.infrastructure.persistence.repository.auth;

import org.springframework.data.jpa.repository.*;
import java.util.*;
import com.tiendatech.usuarios.infrastructure.persistence.entity.auth.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByJtiAndRevokedFalse(java.util.UUID jti);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.familyId = ?1")
    void revokeFamily(java.util.UUID familyId);
}
