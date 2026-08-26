package com.tiendatech.pedidos.domain;

import java.time.LocalDate;

/**
 * Puerto de dominio (patron Repository): define el contrato de persistencia de
 * Orden/DetalleOrden sin acoplarse a JDBC/Spring. La implementacion real vive
 * en infrastructure.persistence.JdbcOrdenRepository.
 */
public interface OrdenRepository {

    PageResponse<Orden> listarOrdenes(Paginacion paginacion);

    Orden obtenerPorId(Integer ordenId);

    PageResponse<Orden> listarPorUsuario(Integer usuarioId, Paginacion paginacion);

    PageResponse<DetalleOrden> obtenerDetalle(Integer ordenId, LocalDate fecha, Paginacion paginacion);

    /**
     * idempotencyKey/payloadHash son null cuando el checkout no envio
     * Idempotency-Key (o la funcionalidad esta apagada): en ese caso el
     * comportamiento es identico al de antes de esta funcionalidad.
     */
    Orden crear(Integer usuarioId, Integer direccionId, Integer metodopagoId,
               String idempotencyKey, String payloadHash);
}
