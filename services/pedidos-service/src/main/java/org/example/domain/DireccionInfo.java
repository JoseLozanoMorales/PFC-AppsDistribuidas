package org.example.domain;

public record DireccionInfo(Integer direccionId, Integer usuarioId, String calle, String referencia,
                            Integer ciudadId, String ciudadNombre, String provinciaNombre,
                            Boolean habilitado) {
}
