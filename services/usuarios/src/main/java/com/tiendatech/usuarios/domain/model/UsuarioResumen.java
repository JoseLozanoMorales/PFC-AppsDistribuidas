package com.tiendatech.usuarios.domain.model;

public record UsuarioResumen(Integer id, String nombre, String cedula, String correo, String telefono,
                             String usuario, Integer rolId, Boolean habilitado) {
}
