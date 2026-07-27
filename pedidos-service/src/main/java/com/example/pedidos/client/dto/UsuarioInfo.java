package com.example.pedidos.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioInfo(
        Integer usuarioId,
        String nombre,
        String cedula,
        String correo,
        String telefono,
        String usuario,
        Integer rolId,
        Boolean habilitado
) {
}
