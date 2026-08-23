package org.example.domain;

import java.math.BigDecimal;

/**
 * Puerto de dominio (patron Repository): define el contrato de persistencia de
 * Carrito/CarritoDetalle sin acoplarse a JDBC/Spring. La implementacion real
 * vive en infrastructure.persistence.JdbcCarritoRepository.
 */
public interface CarritoRepository {

    Carrito obtenerActivo(Integer usuarioId);

    Carrito obtenerPorId(Integer carritoId);

    Carrito crear(Integer usuarioId);

    PageResponse<CarritoDetalle> listarDetalle(Integer carritoId, Paginacion paginacion);

    void agregarProducto(Integer carritoId, Integer productoId, Integer cantidad, BigDecimal precioUnitario);

    void quitarProducto(Integer carritoId, Integer productoId);

    void actualizarCantidad(Integer carritoId, Integer productoId, Integer cantidad);
}
