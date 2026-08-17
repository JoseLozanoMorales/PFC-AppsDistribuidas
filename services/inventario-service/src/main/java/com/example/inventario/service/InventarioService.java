package com.example.inventario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventarioService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;

    public InventarioService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                             IdempotencyGuard idempotencyGuard) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.idempotencyGuard = idempotencyGuard;
    }

    public List<Map<String, Object>> listarMovimientos() {
        return jdbc.queryForList("""
                SELECT m.movimiento_id, m.fecha, t.nombre AS tipo, s.nombre AS subtipo,
                       p.nombre AS producto, m.cantidad, m.costo_unitario, m.costo_total,
                       m.referencia, m.observacion, m.producto_id
                FROM inventario.movimiento_inventario m
                JOIN productos.producto p ON p.producto_id = m.producto_id
                JOIN inventario.subtipo_movimiento s ON s.subtipo_id = m.subtipo_id
                JOIN inventario.tipo_movimiento t ON t.tipo_id = s.tipo_id
                ORDER BY m.fecha DESC, m.movimiento_id DESC
                """);
    }

    public List<Map<String, Object>> listarSubtipos(Integer tipo) {
        return jdbc.queryForList("""
                SELECT subtipo_id, nombre, tipo_id FROM inventario.subtipo_movimiento
                WHERE (CAST(? AS INT8) IS NULL OR tipo_id = ?) ORDER BY tipo_id, nombre
                """, tipo, tipo);
    }

    public Map<String, Object> obtenerStock(Integer productoId) {
        if (productoId == null) {
            throw new IllegalArgumentException("Falta productoId");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT producto_id, nombre, stock FROM productos.producto WHERE producto_id = ? AND habilitado",
                productoId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto " + productoId + " no existe");
        }
        return rows.getFirst();
    }

    public List<Map<String, Object>> listarStock(List<Integer> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un productoId");
        }

        List<Integer> ids = productoIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un productoId valido");
        }

        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc.queryForList("""
                SELECT producto_id, nombre, stock FROM productos.producto
                WHERE habilitado AND producto_id IN (%s) ORDER BY producto_id
                """.formatted(placeholders), ids.toArray());
    }

    @Transactional
    public boolean registrarMovimiento(Object body, String usuario, String idempotencyKey) {
        List<Map<String, Object>> items = normalizarItems(body);
        if (idempotencyGuard.isReplay(idempotencyKey, items)) {
            return true;
        }
        for (Map<String, Object> item : items) {
            registrarItem(item);
        }
        return false;
    }

    private void registrarItem(Map<String, Object> item) {
        Integer productoId = entero(item.get("producto_id"));
        Integer subtipoId = entero(item.get("subtipo_id"));
        Integer cantidad = entero(item.get("cantidad"));
        if (productoId == null || subtipoId == null || cantidad == null) {
            throw new IllegalArgumentException("Faltan producto_id, subtipo_id o cantidad");
        }

        String tipo = jdbc.queryForObject("""
                SELECT t.nombre FROM inventario.subtipo_movimiento s
                JOIN inventario.tipo_movimiento t ON t.tipo_id = s.tipo_id
                WHERE s.subtipo_id = ?
                """, String.class, subtipoId);
        if (!"AJUSTE".equalsIgnoreCase(tipo) && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }

        Map<String, Object> producto = jdbc.queryForMap("""
                SELECT stock, costo FROM productos.producto
                WHERE producto_id = ? FOR UPDATE
                """, productoId);
        int stockAnterior = ((Number) producto.get("stock")).intValue();
        BigDecimal costoAnterior = decimal(producto.get("costo"));
        if ("SALIDA".equalsIgnoreCase(tipo) && stockAnterior < cantidad) {
            throw new IllegalArgumentException("Stock insuficiente para el producto " + productoId);
        }

        BigDecimal costoEntrada = decimalNullable(item.get("costo_unitario"));
        BigDecimal costoEfectivo = "ENTRADA".equalsIgnoreCase(tipo) && costoEntrada != null
                ? costoEntrada : costoAnterior;
        int stockNuevo = "SALIDA".equalsIgnoreCase(tipo)
                ? stockAnterior - cantidad : stockAnterior + cantidad;
        BigDecimal costoNuevo = costoAnterior;
        if ("ENTRADA".equalsIgnoreCase(tipo) && costoEntrada != null && stockNuevo > 0) {
            costoNuevo = costoAnterior.multiply(BigDecimal.valueOf(stockAnterior))
                    .add(costoEntrada.multiply(BigDecimal.valueOf(cantidad)))
                    .divide(BigDecimal.valueOf(stockNuevo), 2, RoundingMode.HALF_UP);
        }
        BigDecimal total = costoEfectivo.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
        Timestamp fecha = timestamp(item.get("fecha"));

        jdbc.update("""
                INSERT INTO inventario.movimiento_inventario
                    (fecha, cantidad, costo_unitario, costo_total, referencia,
                     observacion, producto_id, subtipo_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fecha, cantidad, costoEfectivo, total, item.get("referencia"),
                item.get("observacion"), productoId, subtipoId);
        jdbc.update("""
                UPDATE productos.producto SET stock = ?, costo = ?, valor_inventario = ?
                WHERE producto_id = ?
                """, stockNuevo, costoNuevo,
                costoNuevo.multiply(BigDecimal.valueOf(stockNuevo)).setScale(2, RoundingMode.HALF_UP), productoId);
        jdbc.update("""
                UPSERT INTO inventario.inventario_producto
                    (producto_id, stock, stock_minimo, valor_inventario, actualizado_en)
                VALUES (?, ?, COALESCE((SELECT stock_minimo FROM inventario.inventario_producto
                                        WHERE producto_id = ?), 0), ?, now())
                """, productoId, stockNuevo, productoId,
                costoNuevo.multiply(BigDecimal.valueOf(stockNuevo)).setScale(2, RoundingMode.HALF_UP));
        jdbc.update("""
                INSERT INTO inventario.kardex_inventario
                    (fecha, tipo_operacion, cantidad_entrada, costo_unitario_entrada,
                     costo_total_entrada, cantidad_salida, costo_unitario_salida,
                     costo_total_salida, saldo_cantidad, saldo_costo_unitario,
                     saldo_total, producto_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, fecha, tipo,
                "ENTRADA".equalsIgnoreCase(tipo) ? cantidad : 0,
                "ENTRADA".equalsIgnoreCase(tipo) ? costoEfectivo : null,
                "ENTRADA".equalsIgnoreCase(tipo) ? total : null,
                "SALIDA".equalsIgnoreCase(tipo) ? cantidad : 0,
                "SALIDA".equalsIgnoreCase(tipo) ? costoEfectivo : null,
                "SALIDA".equalsIgnoreCase(tipo) ? total : null,
                stockNuevo, costoNuevo,
                costoNuevo.multiply(BigDecimal.valueOf(stockNuevo)).setScale(2, RoundingMode.HALF_UP), productoId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizarItems(Object body) {
        if (body instanceof List<?> lista) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object value : lista) items.add(objectMapper.convertValue(value, LinkedHashMap.class));
            return items;
        }
        return List.of(objectMapper.convertValue(body, LinkedHashMap.class));
    }

    private static Integer entero(Object value) {
        return value == null ? null : value instanceof Number n ? n.intValue() : Integer.valueOf(value.toString());
    }

    private static BigDecimal decimal(Object value) {
        BigDecimal result = decimalNullable(value);
        return result == null ? BigDecimal.ZERO : result;
    }

    private static BigDecimal decimalNullable(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static Timestamp timestamp(Object value) {
        if (value == null || value.toString().isBlank()) return Timestamp.valueOf(LocalDateTime.now());
        return Timestamp.valueOf(value.toString().replace('T', ' '));
    }
}
