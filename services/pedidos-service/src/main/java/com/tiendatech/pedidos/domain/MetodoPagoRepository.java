package com.tiendatech.pedidos.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto de dominio (patron Repository): define el contrato de persistencia de
 * MetodoPago/TipoMetodoPago sin acoplarse a JDBC/Spring. La implementacion real
 * vive en infrastructure.persistence.JdbcMetodoPagoRepository.
 */
public interface MetodoPagoRepository {

    PageResponse<MetodoPago> listarPorUsuario(Integer usuarioId, Paginacion paginacion);

    MetodoPago obtenerPorIdYUsuario(Integer metodopagoId, Integer usuarioId);

    List<TipoMetodoPago> listarTipos();

    Integer agregar(String numeroTarjeta, LocalDate fechaExpiracion, Integer tipoId, Integer usuarioId);

    void actualizar(Integer metodopagoId, Integer usuarioId, String numeroTarjeta,
                    LocalDate fechaExpiracion, Integer tipoId, Boolean habilitado);

    void inactivar(Integer metodopagoId, Integer usuarioId);

    void reactivar(Integer metodopagoId, Integer usuarioId);
}
