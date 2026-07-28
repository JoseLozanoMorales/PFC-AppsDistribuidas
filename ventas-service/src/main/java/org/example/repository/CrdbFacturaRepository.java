package org.example.repository;

import org.example.model.Factura;
import org.example.model.FacturaDetalle;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("crdb")
public class CrdbFacturaRepository implements FacturaStore {

    private final JdbcTemplate jdbcTemplate;

    public CrdbFacturaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Integer generarDesdeOrden(Integer ordenId) {
        List<Integer> existentes = jdbcTemplate.query(
                "SELECT factura_id FROM ventas.factura_encabezado WHERE orden_id = ?",
                (rs, rowNum) -> rs.getInt("factura_id"), ordenId);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }

        Integer facturaId = jdbcTemplate.queryForObject("""
                INSERT INTO ventas.factura_encabezado
                    (orden_id, fecha_orden, usuario_id, subtotal, total, numero)
                SELECT orden_id, fecha, usuario_id, subtotal, total,
                       concat('FAC-E3-', CAST(orden_id AS STRING))
                FROM pedidos.orden
                WHERE orden_id = ?
                RETURNING factura_id
                """, Integer.class, ordenId);

        jdbcTemplate.update("""
                INSERT INTO ventas.factura_cuerpo
                    (factura_id, producto_id, nombre_producto, cantidad,
                     precio, subtotal, iva, total)
                SELECT ?, producto_id, concat('Producto ', CAST(producto_id AS STRING)), cantidad,
                       precio_unitario, subtotal, iva, total
                FROM pedidos.detalle_orden
                WHERE orden_id = ?
                """, facturaId, ordenId);
        return facturaId;
    }

    @Override
    public Factura obtenerPorId(Integer facturaId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT f.factura_id, f.orden_id, f.usuario_id, f.fecha_emision,
                           f.fecha_orden, u.nombre, u.correo, f.subtotal, f.total, f.numero
                    FROM ventas.factura_encabezado f
                    JOIN usuarios.usuario u ON u.usuario_id = f.usuario_id
                    WHERE f.factura_id = ?
                    """, (rs, rowNum) -> new Factura(
                    rs.getInt("factura_id"), rs.getInt("orden_id"), rs.getInt("usuario_id"),
                    rs.getDate("fecha_emision").toLocalDate(), rs.getDate("fecha_orden").toLocalDate(),
                    null, rs.getString("nombre"), rs.getString("correo"), null, null,
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
                       f.fecha_orden, u.nombre, u.correo, f.subtotal, f.total, f.numero
                FROM ventas.factura_encabezado f
                JOIN usuarios.usuario u ON u.usuario_id = f.usuario_id
                """;
        String sql = usuarioId == null
                ? base + " ORDER BY f.fecha_emision DESC, f.factura_id DESC"
                : base + " WHERE f.usuario_id = ? ORDER BY f.fecha_emision DESC, f.factura_id DESC";
        Object[] args = usuarioId == null ? new Object[0] : new Object[]{usuarioId};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Factura(
                rs.getInt("factura_id"), rs.getInt("orden_id"), rs.getInt("usuario_id"),
                rs.getDate("fecha_emision").toLocalDate(), rs.getDate("fecha_orden").toLocalDate(),
                null, rs.getString("nombre"), rs.getString("correo"), null, null,
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("total"), rs.getString("numero")
        ), args);
    }
}
