package com.example.pedidos.service;

import com.example.pedidos.client.ProductoClient;
import com.example.pedidos.client.dto.ProductoPrecioIva;
import com.example.pedidos.model.Carrito;
import com.example.pedidos.model.CarritoDetalle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CarritoService {

    private final JdbcTemplate jdbcTemplate;
    private final ProductoClient productoClient;

    @Autowired
    public CarritoService(JdbcTemplate jdbcTemplate, ProductoClient productoClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.productoClient = productoClient;
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

    public Carrito obtenerCarritoActivo(Integer usuarioId) {
        String sql = "SELECT carrito_id, usuario_id, total, habilitado " +
                "FROM pedidos.carrito_de_compra " +
                "WHERE usuario_id = ? AND habilitado = true";
        List<Carrito> resultado = jdbcTemplate.query(sql, carritoRowMapper, usuarioId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    public Carrito obtenerCarritoPorId(Integer carritoId) {
        String sql = "SELECT carrito_id, usuario_id, total, habilitado " +
                "FROM pedidos.carrito_de_compra WHERE carrito_id = ?";
        List<Carrito> resultado = jdbcTemplate.query(sql, carritoRowMapper, carritoId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    public Carrito crearCarrito(Integer usuarioId) {
        String sql = "INSERT INTO pedidos.carrito_de_compra (usuario_id, total, habilitado) " +
                "VALUES (?, 0, true) RETURNING carrito_id";
        Integer nuevoId = jdbcTemplate.queryForObject(sql, Integer.class, usuarioId);
        return new Carrito(nuevoId, usuarioId, BigDecimal.ZERO, true);
    }

    public List<CarritoDetalle> listarDetalle(Integer carritoId) {
        String sql = "SELECT carrito_id, producto_id, cantidad, precio_unitario " +
                "FROM pedidos.carrito_detalle WHERE carrito_id = ?";
        return jdbcTemplate.query(sql, detalleRowMapper, carritoId);
    }

    // Ahora el precio NO viene del cliente -- se consulta a productos-service
    // para evitar que alguien manipule el precio desde el frontend.
    public void agregarProducto(Integer carritoId, Integer productoId, Integer cantidad) {
        ProductoPrecioIva info = productoClient.obtenerPrecioEIva(productoId);

        String sql = "INSERT INTO pedidos.carrito_detalle (carrito_id, producto_id, cantidad, precio_unitario) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (carrito_id, producto_id) " +
                "DO UPDATE SET cantidad = pedidos.carrito_detalle.cantidad + EXCLUDED.cantidad";
        jdbcTemplate.update(sql, carritoId, productoId, cantidad, info.precioUnitario());
        // El trigger fntg_carrito_detalle_recalc ya actualiza el total automáticamente
    }

    public void quitarProducto(Integer carritoId, Integer productoId) {
        String sql = "DELETE FROM pedidos.carrito_detalle WHERE carrito_id = ? AND producto_id = ?";
        jdbcTemplate.update(sql, carritoId, productoId);
        // El trigger también se dispara en DELETE
    }

    public void actualizarCantidad(Integer carritoId, Integer productoId, Integer cantidad) {
        if (cantidad <= 0) {
            quitarProducto(carritoId, productoId);
            return;
        }
        String sql = "UPDATE pedidos.carrito_detalle SET cantidad = ? WHERE carrito_id = ? AND producto_id = ?";
        jdbcTemplate.update(sql, cantidad, carritoId, productoId);
    }
}