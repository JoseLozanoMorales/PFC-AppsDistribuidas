package com.example.pedidos.service;

import com.example.pedidos.model.Orden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class OrdenPersistenceService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OrdenPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Orden crearOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId) {

        String sqlCarrito = "SELECT carrito_id FROM pedidos.carrito_de_compra " +
                "WHERE usuario_id = ? AND habilitado = true";
        List<Integer> carritos = jdbcTemplate.query(sqlCarrito,
                (rs, rowNum) -> rs.getInt("carrito_id"), usuarioId);

        if (carritos.isEmpty()) {
            throw new IllegalStateException("El usuario " + usuarioId + " no tiene carrito activo");
        }
        Integer carritoId = carritos.get(0);

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

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleCarritoTmp item : items) {
            BigDecimal lineaSubtotal = item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad));
            BigDecimal factorIva = BigDecimal.ONE.add(item.porcentajeIva.divide(BigDecimal.valueOf(100)));
            BigDecimal lineaTotal = lineaSubtotal.multiply(factorIva);
            subtotal = subtotal.add(lineaSubtotal);
            total = total.add(lineaTotal);
        }

        String sqlOrden = "INSERT INTO pedidos.orden (usuario_id, direccion_id, metodopago_id, subtotal, total, fecha) " +
                "VALUES (?, ?, ?, ?, ?, ?) RETURNING orden_id";
        LocalDate hoy = LocalDate.now();
        Integer ordenId = jdbcTemplate.queryForObject(sqlOrden, Integer.class,
                usuarioId, direccionId, metodopagoId, subtotal, total, hoy);

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

        String sqlVaciar = "DELETE FROM pedidos.carrito_detalle WHERE carrito_id = ?";
        jdbcTemplate.update(sqlVaciar, carritoId);

        return new Orden(ordenId, usuarioId, direccionId, metodopagoId, subtotal, total, hoy);
    }

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