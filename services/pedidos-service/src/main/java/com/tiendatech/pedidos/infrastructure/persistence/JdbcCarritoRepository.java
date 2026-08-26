package com.tiendatech.pedidos.infrastructure.persistence;

import com.tiendatech.pedidos.domain.Carrito;
import com.tiendatech.pedidos.domain.CarritoDetalle;
import com.tiendatech.pedidos.domain.CarritoRepository;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.Paginacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adaptador JDBC (patron Repository) del puerto domain.CarritoRepository.
 * Toda la logica SQL se mantiene identica a la version previa: este refactor
 * es puramente estructural, no cambia comportamiento.
 */
@Repository
public class JdbcCarritoRepository implements CarritoRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCarritoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Carrito> carritoRowMapper = (rs, rowNum) -> new Carrito(
            rs.getInt("carrito_id"),
            rs.getInt("usuario_id"),
            rs.getBigDecimal("total"),
            rs.getBoolean("habilitado")
    );

    private final RowMapper<CarritoDetalle> detalleRowMapper = (rs, rowNum) -> new CarritoDetalle(
            rs.getInt("carrito_id"),
            rs.getInt("producto_id"),
            rs.getInt("cantidad"),
            rs.getBigDecimal("precio_unitario")
    );

    @Override
    public Carrito obtenerActivo(Integer usuarioId) {
        String sql = "SELECT carrito_id, usuario_id, total, habilitado " +
                "FROM pedidos.carrito_de_compra " +
                "WHERE usuario_id = ? AND habilitado = true";
        List<Carrito> resultado = jdbcTemplate.query(sql, carritoRowMapper, usuarioId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public Carrito obtenerPorId(Integer carritoId) {
        String sql = "SELECT carrito_id, usuario_id, total, habilitado " +
                "FROM pedidos.carrito_de_compra WHERE carrito_id = ?";
        List<Carrito> resultado = jdbcTemplate.query(sql, carritoRowMapper, carritoId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public Carrito crear(Integer usuarioId) {
        String sql = "INSERT INTO pedidos.carrito_de_compra (usuario_id, total, habilitado) " +
                "VALUES (?, 0, true) RETURNING carrito_id";
        Integer nuevoId = jdbcTemplate.queryForObject(sql, Integer.class, usuarioId);
        return new Carrito(nuevoId, usuarioId, BigDecimal.ZERO, true);
    }

    @Override
    public PageResponse<CarritoDetalle> listarDetalle(Integer carritoId, Paginacion paginacion) {
        String sql = "SELECT carrito_id, producto_id, cantidad, precio_unitario " +
                "FROM pedidos.carrito_detalle WHERE carrito_id = ? " +
                "ORDER BY producto_id LIMIT ? OFFSET ?";
        List<CarritoDetalle> contenido = jdbcTemplate.query(
                sql, detalleRowMapper, carritoId, paginacion.size(), paginacion.offset());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.carrito_detalle WHERE carrito_id = ?", Long.class, carritoId);
        return PageResponse.of(contenido, paginacion, total == null ? 0 : total);
    }

    @Override
    public void agregarProducto(Integer carritoId, Integer productoId, Integer cantidad, BigDecimal precioUnitario) {
        String sql = "INSERT INTO pedidos.carrito_detalle (carrito_id, producto_id, cantidad, precio_unitario) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (carrito_id, producto_id) " +
                "DO UPDATE SET cantidad = pedidos.carrito_detalle.cantidad + EXCLUDED.cantidad";
        jdbcTemplate.update(sql, carritoId, productoId, cantidad, precioUnitario);
        // El trigger fntg_carrito_detalle_recalc ya actualiza el total automáticamente
    }

    @Override
    public void quitarProducto(Integer carritoId, Integer productoId) {
        String sql = "DELETE FROM pedidos.carrito_detalle WHERE carrito_id = ? AND producto_id = ?";
        jdbcTemplate.update(sql, carritoId, productoId);
        // El trigger también se dispara en DELETE
    }

    @Override
    public void actualizarCantidad(Integer carritoId, Integer productoId, Integer cantidad) {
        if (cantidad <= 0) {
            quitarProducto(carritoId, productoId);
            return;
        }
        String sql = "UPDATE pedidos.carrito_detalle SET cantidad = ? WHERE carrito_id = ? AND producto_id = ?";
        jdbcTemplate.update(sql, cantidad, carritoId, productoId);
    }
}
