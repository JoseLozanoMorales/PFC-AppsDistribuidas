package com.tiendatech.usuarios.domain.model.auth;

import java.util.UUID;

/** Datos autenticados extraidos de un refresh token valido. */
public record RefreshClaims(UUID jti, UUID familyId, Integer userId, String role) {
}
