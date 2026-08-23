package org.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de dominio (patron Repository): define el contrato de persistencia de
 * OrdenCompra sin acoplarse a JDBC/Spring. La implementacion real vive en
 * infrastructure.persistence.JdbcOrdenCompraRepository.
 */
public interface OrdenCompraRepository {

    Integer crear(Integer proveedorId, Integer usuarioId, LocalDate fechaEsperada,
                  List<DetalleOrdenCompra> detalle);

    void actualizar(Integer id, Integer proveedorId, LocalDate fechaEsperada,
                    List<DetalleOrdenCompra> detalle);

    void enviar(Integer id);

    void cancelar(Integer id);

    // Devuelve el costo_unitario negociado (de detalle_orden_compra) por cada producto
    // recibido, para que quien llama pueda pasarselo a inventario-service y el kardex
    // recalcule el costo promedio ponderado con el costo real de compra.
    Map<Integer, BigDecimal> registrarRecepcion(Integer id, Map<Integer, Integer> recepcion);

    List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado);

    OrdenCompra obtenerPorId(Integer id);

    List<DetalleOrdenCompra> listarDetalle(Integer ordenCompraId);
}
