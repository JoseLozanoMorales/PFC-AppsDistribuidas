package org.example.domain;

public record UsuarioInfo(Integer usuarioId, String nombre, String cedula, String correo,
                          String telefono, String usuario, Integer rolId, Boolean habilitado) {
}
