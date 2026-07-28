package com.example.pedidos.service;

import com.example.pedidos.client.ProductoClient;
import com.example.pedidos.client.UsuarioClient;
import com.example.pedidos.client.dto.DireccionInfo;
import com.example.pedidos.client.dto.ProductoPrecioIva;
import com.example.pedidos.client.dto.UsuarioInfo;
import com.example.pedidos.model.Orden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class OrdenPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ProductoClient productoClient;
    private final UsuarioClient usuarioClient;

    @Autowired
    public OrdenPersistenceService(JdbcTemplate jdbcTemplate, ProductoClient productoClient,
                                   UsuarioClient usuarioClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.productoClient = productoClient;
        this.usuarioClient = usuarioClient;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Orden crearOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId) {

        // 0) Validar usuario y dirección antes de tocar la base -- evita crear
        //    órdenes con datos inconsistentes o inexistentes.
        UsuarioInfo usuario;
        try {
            usuario = usuarioClient.obtenerUsuario(usuarioId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Usuario " + usuarioId + " no existe en usuarios-service", e);
        }

        List<DireccionInfo> direcciones = usuarioClient.obtenerDirecciones(usuarioId);
        boolean direccionValida = direcciones.stream()
                .anyMatch(d -> direccionId.equals(d.direccionId()) && Boolean.TRUE.equals(d.habilitado()));

        if (!direccionValida) {
            throw new IllegalArgumentException(
                    "La direccion " + direccionId + " no pertenece al usuario " + usuarioId + " o está deshabilitada");
        }

        // 1) Buscar carrito activo
        String sqlCarrito = "SELECT carrito_id FROM pedidos.carrito_de_compra " +
                "WHERE usuario_id = ? AND habilitado = true";
        List<Integer> carritos = jdbcTemplate.query(sqlCarrito,
                (rs, rowNum) -> rs.getInt("carrito_id"), usuarioId);

        if (carritos.isEmpty()) {
            throw new IllegalStateException("El usuario " + usuarioId + " no tiene carrito activo");
        }
        Integer carritoId = carritos.get(0);

        // 2) Traer detalle del carrito, con precio/IVA real desde productos-service
        String sqlDetalle = "SELECT producto_id, cantidad FROM pedidos.carrito_detalle WHERE carrito_id = ?";
        List<DetalleCarritoTmp> items = jdbcTemplate.query(sqlDetalle, (rs, rowNum) -> {
            Integer productoId = rs.getInt("producto_id");
            Integer cantidad = rs.getInt("cantidad");
            ProductoPrecioIva info = productoClient.obtenerPrecioEIva(productoId);
            return new DetalleCarritoTmp(productoId, cantidad, info.precioUnitario(), info.porcentajeIva());
        }, carritoId);

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
