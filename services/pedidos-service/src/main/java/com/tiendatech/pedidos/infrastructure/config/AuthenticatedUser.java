package com.tiendatech.pedidos.infrastructure.config;

public record AuthenticatedUser(Integer userId, String username, String role) {

    private static final String ROL_ADMIN = "ADMIN";

    public boolean esAdmin() {
        return ROL_ADMIN.equalsIgnoreCase(role);
    }
}
