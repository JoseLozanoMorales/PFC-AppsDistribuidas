package com.example.pedidos.service;

import com.example.pedidos.client.FacturaClient;
import com.example.pedidos.config.CrdbMetrics;
import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import com.example.pedidos.paging.PageResponse;
import com.example.pedidos.paging.Paginacion;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Service
public class OrdenService {

    private final JdbcTemplate jdbcTemplate;
    private final FacturaClient facturaClient;
    private final OrdenPersistenceService ordenPersistenceService;
    private final ObjectProvider<CrdbRetryExecutor> crdbRetryExecutor;
    private final ObjectProvider<CrdbMetrics> crdbMetrics;

    @Autowired
    public OrdenService(JdbcTemplate jdbcTemplate, FacturaClient facturaClient,
                        OrdenPersistenceService ordenPersistenceService,
                        ObjectProvider<CrdbRetryExecutor> crdbRetryExecutor,
                        ObjectProvider<CrdbMetrics> crdbMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.facturaClient = facturaClient;
        this.ordenPersistenceService = ordenPersistenceService;
        this.crdbRetryExecutor = crdbRetryExecutor;
        this.crdbMetrics = crdbMetrics;
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

    // COUNT(*) sobre pedidos.orden: medido con EXPLAIN ANALYZE en ~271ms con
    // 600K filas (full scan, no hay forma de evitarlo con un conteo exacto).
    // Decision consciente: se acepta ese costo fijo por pagina a cambio de
    // simplicidad y totalElements/totalPages siempre correctos. Si con el
    // crecimiento de la tabla esto domina la latencia, cambiar por una
    // aproximacion basada en estadisticas de la tabla.
    public PageResponse<Orden> listarOrdenes(Paginacion paginacion) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden ORDER BY fecha DESC, orden_id DESC LIMIT ? OFFSET ?";
        List<Orden> contenido = medirConsulta(() -> jdbcTemplate.query(
                sql, ordenRowMapper, paginacion.size(), paginacion.offset()));
        long total = medirConsulta(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.orden", Long.class));
        return PageResponse.of(contenido, paginacion, total);
    }

    public Orden obtenerOrdenPorId(Integer ordenId) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE orden_id = ?";
        List<Orden> resultado = medirConsulta(
                () -> jdbcTemplate.query(sql, ordenRowMapper, ordenId));
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    public PageResponse<Orden> listarOrdenesPorUsuario(Integer usuarioId, Paginacion paginacion) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE usuario_id = ? ORDER BY fecha DESC, orden_id DESC LIMIT ? OFFSET ?";
        List<Orden> contenido = medirConsulta(() -> jdbcTemplate.query(
                sql, ordenRowMapper, usuarioId, paginacion.size(), paginacion.offset()));
        long total = medirConsulta(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.orden WHERE usuario_id = ?", Long.class, usuarioId));
        return PageResponse.of(contenido, paginacion, total);
    }

    // fecha se recibe ademas de ordenId (el llamador ya tiene la Orden completa
    // por el chequeo de propiedad) porque la PK de detalle_orden es (fecha,
    // detalle_id) y el unico indice secundario es (fecha, orden_id): filtrar
    // solo por orden_id fuerza un FULL SCAN de la tabla (570ms medido con
    // EXPLAIN ANALYZE sobre 600K filas). Con fecha en el WHERE, CRDB usa
    // idx_detalle_orden y resuelve en unos pocos ms.
    public PageResponse<DetalleOrden> obtenerDetalleOrden(Integer ordenId, LocalDate fecha, Paginacion paginacion) {
        String sql = "SELECT orden_id, producto_id, cantidad, precio_unitario, subtotal, iva, total " +
                "FROM pedidos.detalle_orden WHERE fecha = ? AND orden_id = ? ORDER BY detalle_id LIMIT ? OFFSET ?";
        List<DetalleOrden> contenido = medirConsulta(() -> jdbcTemplate.query(sql, (rs, rowNum) -> new DetalleOrden(
                rs.getInt("orden_id"),
                rs.getInt("producto_id"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("iva"),
                rs.getBigDecimal("total")
        ), fecha, ordenId, paginacion.size(), paginacion.offset()));
        long total = medirConsulta(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.detalle_orden WHERE fecha = ? AND orden_id = ?",
                Long.class, fecha, ordenId));
        return PageResponse.of(contenido, paginacion, total);
    }

    // Orquesta: 1) crea la orden en una transacción que hace COMMIT antes de seguir,
    // 2) recién después llama a ventas-service (ya puede ver la orden en la BD).
    public Orden generarOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId) {
        CrdbRetryExecutor retry = crdbRetryExecutor.getIfAvailable();
        Orden orden = retry == null
                ? ordenPersistenceService.crearOrdenDesdeCarrito(usuarioId, direccionId, metodopagoId)
                : retry.execute(() -> ordenPersistenceService.crearOrdenDesdeCarrito(
                        usuarioId, direccionId, metodopagoId));

        try {
            facturaClient.generarFactura(orden.getOrdenId());
        } catch (Exception e) {
            // La orden YA está confirmada en la BD (commit hecho). Decide con el equipo:
            // reintento, marcar "pendiente_facturacion", o solo log + seguir.
            throw new IllegalStateException("Orden " + orden.getOrdenId() + " creada pero falló la facturación", e);
        }

        return orden;
    }

    private <T> T medirConsulta(Supplier<T> consulta) {
        CrdbMetrics metrics = crdbMetrics.getIfAvailable();
        if (metrics == null) {
            return consulta.get();
        }

        Timer.Sample sample = metrics.startQuery();
        try {
            return consulta.get();
        } finally {
            metrics.stopQuery(sample);
        }
    }
}
