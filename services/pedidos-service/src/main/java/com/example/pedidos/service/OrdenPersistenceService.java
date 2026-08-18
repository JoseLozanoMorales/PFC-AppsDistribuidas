package com.example.pedidos.service;

import com.example.pedidos.client.ProductoClient;
import com.example.pedidos.client.UsuarioClient;
import com.example.pedidos.client.dto.DireccionInfo;
import com.example.pedidos.client.dto.ProductoPrecioIva;
import com.example.pedidos.client.dto.UsuarioInfo;
import com.example.pedidos.idempotencia.IdempotenciaRepository;
import com.example.pedidos.model.Orden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class OrdenPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ProductoClient productoClient;
    private final UsuarioClient usuarioClient;
    private final Optional<IdempotenciaRepository> idempotenciaRepository;

    @Autowired
    public OrdenPersistenceService(JdbcTemplate jdbcTemplate, ProductoClient productoClient,
                                   UsuarioClient usuarioClient,
                                   Optional<IdempotenciaRepository> idempotenciaRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productoClient = productoClient;
        this.usuarioClient = usuarioClient;
        this.idempotenciaRepository = idempotenciaRepository;
    }

    /**
     * idempotencyKey/payloadHash son null cuando el checkout no envio
     * Idempotency-Key (o la funcionalidad esta apagada): en ese caso el
     * comportamiento es identico al de antes de esta funcionalidad.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Orden crearOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId,
                                         String idempotencyKey, String payloadHash) {

        // 0) Validar usuario y dirección
        // Solo un 404 real de usuarios-service significa "no existe" (400). Cualquier
        // otra falla (circuit breaker abierto, timeout, 5xx) NO es lo mismo y no debe
        // camuflarse como error del cliente: se deja propagar para que el handler
        // correspondiente (503 en ambos casos) lo reporte tal cual es.
        UsuarioInfo usuario;
        try {
            usuario = usuarioClient.obtenerUsuario(usuarioId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Usuario " + usuarioId + " no existe en usuarios-service", e);
        }

        List<DireccionInfo> direcciones = usuarioClient.obtenerDirecciones(usuarioId);
        boolean direccionValida = direcciones.stream()
                .anyMatch(d -> direccionId.equals(d.direccionId()) && Boolean.TRUE.equals(d.habilitado()));

        if (!direccionValida) {
            throw new IllegalArgumentException(
                    "La direccion " + direccionId + " no pertenece al usuario " + usuarioId + " o está deshabilitada");
        }

        // 0.1) Validar método de pago
        String sqlMetodo = "SELECT COUNT(*) FROM pedidos.metodopago " +
                "WHERE metodopago_id = ? AND usuario_id = ? AND habilitado = true";
        Integer countMetodo = jdbcTemplate.queryForObject(sqlMetodo, Integer.class, metodopagoId, usuarioId);
        if (countMetodo == null || countMetodo == 0) {
            throw new IllegalArgumentException(
                    "El metodo de pago " + metodopagoId + " no existe, no pertenece al usuario " + usuarioId + " o esta deshabilitado");
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

        // 7) Registrar la solicitud idempotente en la MISMA transaccion: si otra
        // peticion concurrente con la misma clave ya gano la carrera, esto lanza
        // ClaveIdempotenciaEnConflictoException y hace rollback de todo lo anterior
        // (orden y detalle incluidos). El llamador (OrdenService) atrapa esa
        // excepcion y devuelve la orden de la solicitud ganadora en su lugar.
        if (idempotencyKey != null) {
            idempotenciaRepository.ifPresent(repo -> repo.registrar(usuarioId, idempotencyKey, payloadHash, ordenId));
        }

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
