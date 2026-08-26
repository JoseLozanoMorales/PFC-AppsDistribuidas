package com.tiendatech.pedidos.domain;

import java.util.Optional;

/**
 * Puerto para el registro de solicitudes idempotentes (hoy solo checkout).
 * Ver services/pedidos-service/docs/idempotencia.sql para el DDL de la tabla
 * que respalda la unica implementacion real ({@link com.tiendatech.pedidos.infrastructure.persistence.JdbcIdempotenciaRepository})
 * y para las condiciones que la activan.
 */
public interface IdempotenciaRepository {

    Optional<SolicitudIdempotente> buscarPorUsuarioYClave(Integer usuarioId, String clave);

    /**
     * Registra la solicitud dentro de la MISMA transaccion que crea el recurso
     * (atomicidad va PRIMARY KEY (usuario_id, clave)). Si otra transaccion
     * concurrente con la misma clave ya gano la carrera, lanza
     * {@link ClaveIdempotenciaEnConflictoException} en vez de duplicar la fila.
     */
    void registrar(Integer usuarioId, String clave, String payloadHash, Integer ordenId);
}
