package com.tiendatech.usuarios.domain.model.auth;

public record AccessClaims(Integer userId, String username, String role) {
}
