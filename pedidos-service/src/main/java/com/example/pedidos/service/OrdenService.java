package com.example.pedidos.service;

import com.example.pedidos.client.FacturaClient;
import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrdenService {

    private final JdbcTemplate jdbcTemplate;
    private final FacturaClient facturaClient;
    private final OrdenPersistenceService ordenPersistenceService;

    @Autowired
    public OrdenService(JdbcTemplate jdbcTemplate, FacturaClient facturaClient,
                        OrdenPersistenceService ordenPersistenceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.facturaClient = facturaClient;
        this.ordenPersistenceService = ordenPersistenceService;
    }

    private final RowMapper<Orden> ordenRowMapper = (rs, rowNum) -> new Orden(
            rs.getInt("orden_id"),
            rs.getInt("usuario_id"),
            rs.getInt("direccion_id"),
            rs.getInt("metodopago_id"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("total"),
            rs.getDate("fecha").toLocalDate()
    );

    public List<Orden> listarOrdenes() {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden ORDER BY fecha DESC";
        return jdbcTemplate.query(sql, ordenRowMapper);
    }

    public Orden obtenerOrdenPorId(Integer ordenId) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE orden_id = ?";
        List<Orden> resultado = jdbcTemplate.query(sql, ordenRowMapper, ordenId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    public List<Orden> listarOrdenesPorUsuario(Integer usuarioId) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE usuario_id = ? ORDER BY fecha DESC";
        return jdbcTemplate.query(sql, ordenRowMapper, usuarioId);
    }

    public List<DetalleOrden> obtenerDetalleOrden(Integer ordenId) {
        String sql = "SELECT orden_id, producto_id, cantidad, precio_unitario, subtotal, iva, total " +
                "FROM pedidos.detalle_orden WHERE orden_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DetalleOrden(
                rs.getInt("orden_id"),
                rs.getInt("producto_id"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("iva"),
                rs.getBigDecimal("total")
        ), ordenId);
    }

    // Orquesta: 1) crea la orden en una transacción que hace COMMIT antes de seguir,
    // 2) recién después llama a ventas-service (ya puede ver la orden en la BD).
    public Orden generarOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId) {
        Orden orden = ordenPersistenceService.crearOrdenDesdeCarrito(usuarioId, direccionId, metodopagoId);

        try {
            facturaClient.generarFactura(orden.getOrdenId());
        } catch (Exception e) {
            // La orden YA está confirmada en la BD (commit hecho). Decide con el equipo:
            // reintento, marcar "pendiente_facturacion", o solo log + seguir.
            throw new IllegalStateException("Orden " + orden.getOrdenId() + " creada pero falló la facturación", e);
        }

        return orden;
    }
}