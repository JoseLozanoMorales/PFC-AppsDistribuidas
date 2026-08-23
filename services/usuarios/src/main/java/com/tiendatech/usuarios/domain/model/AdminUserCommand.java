package com.tiendatech.usuarios.domain.model;

public record AdminUserCommand(String action, Integer id, Integer roleId, String nombre, String cedula,
                               String correo, String telefono, String username, String passwordHash) {
}
