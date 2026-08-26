package com.tiendatech.pedidos.domain;

public record DireccionInfo(Integer direccionId, Integer usuarioId, String calle, String referencia,
                            Integer ciudadId, String ciudadNombre, String provinciaNombre,
                            Boolean habilitado) {
}
