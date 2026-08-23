package com.tiendatech.usuarios.domain.model;

import java.time.OffsetDateTime;

public record LoginAuditoria(Integer idSesion, Integer usuarioId, String usuario, String ip,
                             String host, OffsetDateTime fechaLogin) {
}
