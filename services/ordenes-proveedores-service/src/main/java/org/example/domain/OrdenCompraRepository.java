package org.example.domain;

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

    void registrarRecepcion(Integer id, Map<Integer, Integer> recepcion);

    List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado);

    OrdenCompra obtenerPorId(Integer id);
}
