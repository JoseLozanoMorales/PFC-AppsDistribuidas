package com.example.pedidos.service;

import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class OrdenService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OrdenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    // Obtiene una orden específica por su ID
    public Orden obtenerOrdenPorId(Integer ordenId) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE orden_id = ?";
        List<Orden> resultado = jdbcTemplate.query(sql, ordenRowMapper, ordenId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    // Lista las órdenes de un usuario específico
    public List<Orden> listarOrdenesPorUsuario(Integer usuarioId) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE usuario_id = ? ORDER BY fecha DESC";
        return jdbcTemplate.query(sql, ordenRowMapper, usuarioId);
    }

    // Lista el detalle (items) de una orden específica
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


    // Genera la orden a partir del carrito activo del usuario.
    // NO genera factura ni toca inventario -- eso corresponde a otros microservicios.
    @Transactional
    public Orden generarOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId) {

        // 1) Buscar carrito activo
        String sqlCarrito = "SELECT carrito_id FROM pedidos.carrito_de_compra " +
                "WHERE usuario_id = ? AND habilitado = true";
        List<Integer> carritos = jdbcTemplate.query(sqlCarrito,
                (rs, rowNum) -> rs.getInt("carrito_id"), usuarioId);

        if (carritos.isEmpty()) {
            throw new IllegalStateException("El usuario " + usuarioId + " no tiene carrito activo");
        }
        Integer carritoId = carritos.get(0);

        // 2) Traer detalle del carrito, con IVA desde el esquema productos
        String sqlDetalle =
                "SELECT d.producto_id, d.cantidad, d.precio_unitario, i.porcentaje " +
                        "FROM pedidos.carrito_detalle d " +
                        "JOIN productos.producto p ON p.producto_id = d.producto_id " +
                        "JOIN productos.iva i ON i.iva_id = p.iva_id " +
                        "WHERE d.carrito_id = ?";
        List<DetalleCarritoTmp> items = jdbcTemplate.query(sqlDetalle, (rs, rowNum) -> new DetalleCarritoTmp(
                rs.getInt("producto_id"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"),
                rs.getBigDecimal("porcentaje")
        ), carritoId);

        if (items.isEmpty()) {
            throw new IllegalStateException("El carrito " + carritoId + " está vacío");
        }

        // 3) Calcular subtotal y total
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleCarritoTmp item : items) {
            BigDecimal lineaSubtotal = item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad));
            BigDecimal factorIva = BigDecimal.ONE.add(item.porcentajeIva.divide(BigDecimal.valueOf(100)));
            BigDecimal lineaTotal = lineaSubtotal.multiply(factorIva);
            subtotal = subtotal.add(lineaSubtotal);
            total = total.add(lineaTotal);
        }

        // 4) Insertar orden
        String sqlOrden = "INSERT INTO pedidos.orden (usuario_id, direccion_id, metodopago_id, subtotal, total, fecha) " +
                "VALUES (?, ?, ?, ?, ?, ?) RETURNING orden_id";
        LocalDate hoy = LocalDate.now();
        Integer ordenId = jdbcTemplate.queryForObject(sqlOrden, Integer.class,
                usuarioId, direccionId, metodopagoId, subtotal, total, hoy);

        // 5) Insertar detalle_orden por cada línea del carrito
        String sqlDetalleOrden = "INSERT INTO pedidos.detalle_orden " +
                "(cantidad, precio_unitario, total, subtotal, iva, orden_id, producto_id, fecha) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        for (DetalleCarritoTmp item : items) {
            BigDecimal lineaSubtotal = item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad));
            BigDecimal factorIva = BigDecimal.ONE.add(item.porcentajeIva.divide(BigDecimal.valueOf(100)));
            BigDecimal lineaTotal = lineaSubtotal.multiply(factorIva);
            BigDecimal lineaIva = lineaTotal.subtract(lineaSubtotal);

            jdbcTemplate.update(sqlDetalleOrden,
                    item.cantidad, item.precioUnitario, lineaTotal, lineaSubtotal, lineaIva,
                    ordenId, item.productoId, hoy);
        }

        // 6) Vaciar el carrito (el trigger recalcula el total a 0 automáticamente)
        String sqlVaciar = "DELETE FROM pedidos.carrito_detalle WHERE carrito_id = ?";
        jdbcTemplate.update(sqlVaciar, carritoId);

        return new Orden(ordenId, usuarioId, direccionId, metodopagoId, subtotal, total, hoy);
    }

    // Clase auxiliar interna, solo para transportar datos del carrito durante el cálculo
    private static class DetalleCarritoTmp {
        Integer productoId;
        Integer cantidad;
        BigDecimal precioUnitario;
        BigDecimal porcentajeIva;

        DetalleCarritoTmp(Integer productoId, Integer cantidad, BigDecimal precioUnitario, BigDecimal porcentajeIva) {
            this.productoId = productoId;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.porcentajeIva = porcentajeIva;
        }
    }
}