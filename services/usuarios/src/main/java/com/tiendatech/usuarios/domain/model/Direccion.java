package com.tiendatech.usuarios.domain.model;

public record Direccion(Short id, Integer usuarioId, Short ciudadId, String ciudadNombre,
                        String provinciaNombre, String calle, String referencia, boolean habilitada) {
}
