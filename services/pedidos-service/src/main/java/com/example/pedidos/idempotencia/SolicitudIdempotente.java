package com.example.pedidos.idempotencia;

import java.time.Instant;

public record SolicitudIdempotente(
        Integer usuarioId,
        String clave,
        String payloadHash,
        Integer ordenId,
        Instant creadoEn) {
}
