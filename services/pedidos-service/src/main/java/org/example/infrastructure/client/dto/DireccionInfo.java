package org.example.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DireccionInfo(
        Integer direccionId,
        Integer usuarioId,
        String calle,
        String referencia,
        Integer ciudadId,
        String ciudadNombre,
        String provinciaNombre,
        Boolean habilitado
) {
}
