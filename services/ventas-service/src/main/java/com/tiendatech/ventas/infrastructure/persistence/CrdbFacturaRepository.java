package com.tiendatech.ventas.infrastructure.persistence;

import com.tiendatech.ventas.domain.Factura;
import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.FacturaStore;
import com.tiendatech.ventas.domain.FacturaDraft;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Adaptador JDBC (patron Repository) del puerto domain.FacturaStore.
 * Toda la logica SQL se mantiene identica a la version previa: este refactor
 * es puramente estructural, no cambia comportamiento.
 */
@Repository
public class CrdbFacturaRepository implements FacturaStore {

    private final JdbcTemplate jdbcTemplate;

    public CrdbFacturaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Integer generar(FacturaDraft draft) {
        Integer ordenId = draft.ordenId();
        List<Integer> existentes = jdbcTemplate.query(
                "SELECT factura_id FROM ventas.factura_encabezado WHERE orden_id = ?",
                (rs, rowNum) -> rs.getInt("factura_id"), ordenId);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }

        Integer facturaId = jdbcTemplate.queryForObject("""
                INSERT INTO ventas.factura_encabezado
                    (orden_id, fecha_orden, usuario_id, subtotal, total, numero)
                VALUES (?, ?, ?, ?, ?, concat('FAC-E3-', CAST(? AS STRING)))
                RETURNING factura_id
                """, Integer.class, ordenId, draft.fechaOrden(), draft.usuarioId(),
                draft.subtotal(), draft.total(), ordenId);

        for (FacturaDraft.Linea linea : draft.lineas()) {
            jdbcTemplate.update("""
                    INSERT INTO ventas.factura_cuerpo
                        (factura_id, producto_id, nombre_producto, cantidad,
                         precio, subtotal, iva, total)
                    VALUES (?, ?, concat('Producto ', CAST(? AS STRING)), ?, ?, ?, ?, ?)
                    """, facturaId, linea.productoId(), linea.productoId(), linea.cantidad(),
                    linea.precio(), linea.subtotal(), linea.iva(), linea.total());
        }

        jdbcTemplate.update("""
                INSERT INTO ventas.factura_outbox (factura_id, estado)
                VALUES (?, 'PENDIENTE')
                ON CONFLICT (factura_id) DO NOTHING
                """, facturaId);
        return facturaId;
    }

    @Override
    public Factura obtenerPorId(Integer facturaId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT f.factura_id, f.orden_id, f.usuario_id, f.fecha_emision,
                           f.fecha_orden, f.cedula, f.nombre, f.correo,
                           f.telefono, f.direccion_entrega,
                           f.subtotal, f.total, f.numero
                    FROM ventas.factura_encabezado f
                    WHERE f.factura_id = ?
                    """, (rs, rowNum) -> new Factura(
                    rs.getInt("factura_id"), rs.getInt("orden_id"), rs.getInt("usuario_id"),
                    rs.getDate("fecha_emision").toLocalDate(), rs.getDate("fecha_orden").toLocalDate(),
                    rs.getString("cedula"), rs.getString("nombre"), rs.getString("correo"),
                    rs.getString("telefono"), rs.getString("direccion_entrega"),
                    rs.getBigDecimal("subtotal"), rs.getBigDecimal("total"), rs.getString("numero")
            ), facturaId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<FacturaDetalle> listarDetalle(Integer facturaId) {
        return jdbcTemplate.query("""
                SELECT factura_id, producto_id, nombre_producto, cantidad,
                       precio, subtotal, iva, total
                FROM ventas.factura_cuerpo
                WHERE factura_id = ?
                ORDER BY producto_id
                """, (rs, rowNum) -> new FacturaDetalle(
                rs.getInt("factura_id"), rs.getInt("producto_id"),
                rs.getString("nombre_producto"), rs.getInt("cantidad"),
                rs.getBigDecimal("precio"), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("iva"), rs.getBigDecimal("total")
        ), facturaId);
    }

    @Override
    public List<Factura> listar(Integer usuarioId) {
        String base = """
                SELECT f.factura_id, f.orden_id, f.usuario_id, f.fecha_emision,
                       f.fecha_orden, f.cedula, f.nombre, f.correo,
                       f.telefono, f.direccion_entrega,
                       f.subtotal, f.total, f.numero
                FROM ventas.factura_encabezado f
                """;
        String sql = usuarioId == null
                ? base + " ORDER BY f.fecha_emision DESC, f.factura_id DESC"
                : base + " WHERE f.usuario_id = ? ORDER BY f.fecha_emision DESC, f.factura_id DESC";
        Object[] args = usuarioId == null ? new Object[0] : new Object[]{usuarioId};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Factura(
                rs.getInt("factura_id"), rs.getInt("orden_id"), rs.getInt("usuario_id"),
                rs.getDate("fecha_emision").toLocalDate(), rs.getDate("fecha_orden").toLocalDate(),
                rs.getString("cedula"), rs.getString("nombre"), rs.getString("correo"),
                rs.getString("telefono"), rs.getString("direccion_entrega"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("total"), rs.getString("numero")
        ), args);
    }

    @Override
    public List<Map<String, Object>> masVendidos(int limite) {
        return jdbcTemplate.queryForList("""
                SELECT producto_id AS "productoId", sum(cantidad) AS unidades
                FROM ventas.factura_cuerpo
                GROUP BY producto_id
                ORDER BY unidades DESC, producto_id
                LIMIT ?
                """, limite);
    }
}
