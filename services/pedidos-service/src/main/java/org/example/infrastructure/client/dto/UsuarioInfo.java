package org.example.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioInfo(
        Integer usuarioId,
        String nombre,
        String cedula,
        String correo,
        String telefono,
        String usuario,
        @JsonProperty("id_rol") Integer rolId,
        Boolean habilitado
) {
}
