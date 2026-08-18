package com.example.pedidos.service;

import com.example.pedidos.client.FacturaClient;
import com.example.pedidos.config.CrdbMetrics;
import com.example.pedidos.idempotencia.ClaveIdempotenciaEnConflictoException;
import com.example.pedidos.idempotencia.IdempotenciaRepository;
import com.example.pedidos.idempotencia.SolicitudIdempotente;
import com.example.pedidos.model.DetalleOrden;
import com.example.pedidos.model.Orden;
import com.example.pedidos.paging.PageResponse;
import com.example.pedidos.paging.Paginacion;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    private final JdbcTemplate jdbcTemplate;
    private final FacturaClient facturaClient;
    private final OrdenPersistenceService ordenPersistenceService;
    private final ObjectProvider<CrdbRetryExecutor> crdbRetryExecutor;
    private final ObjectProvider<CrdbMetrics> crdbMetrics;
    private final Optional<IdempotenciaRepository> idempotenciaRepository;

    @Autowired
    public OrdenService(JdbcTemplate jdbcTemplate, FacturaClient facturaClient,
                        OrdenPersistenceService ordenPersistenceService,
                        ObjectProvider<CrdbRetryExecutor> crdbRetryExecutor,
                        ObjectProvider<CrdbMetrics> crdbMetrics,
                        Optional<IdempotenciaRepository> idempotenciaRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.facturaClient = facturaClient;
        this.ordenPersistenceService = ordenPersistenceService;
        this.crdbRetryExecutor = crdbRetryExecutor;
        this.crdbMetrics = crdbMetrics;
        this.idempotenciaRepository = idempotenciaRepository;
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
    //
    // idempotencyKey es opcional (header Idempotency-Key de POST /checkout, ver
    // OrdenController). Si viene y el repositorio esta activo (ver
    // docs/idempotencia.sql), se resuelve en 3 pasos:
    //   1) pre-chequeo (fuera de transaccion, solo optimizacion): si ya existe una
    //      solicitud con esa clave para el usuario, devuelve esa orden sin repetir
    //      todo el flujo ni volver a llamar a facturacion. Hash distinto -> 409.
    //   2) si no existe, ejecuta el flujo normal; crearOrdenDesdeCarrito registra
    //      la clave en la MISMA transaccion que la orden (atomicidad real).
    //   3) si esa insercion choca porque otra peticion concurrente con la misma
    //      clave gano la carrera, se relee esa fila ganadora y se devuelve SU
    //      orden -- es la semantica correcta de una idempotency key concurrente.
    public Orden generarOrdenDesdeCarrito(Integer usuarioId, Integer direccionId, Integer metodopagoId,
                                           String idempotencyKey) {
        IdempotenciaRepository repo = resolverRepositorioIdempotencia(idempotencyKey);
        String payloadHash = repo != null ? calcularPayloadHash(direccionId, metodopagoId) : null;

        if (repo != null) {
            Orden existente = buscarOrdenPorClaveExistente(repo, usuarioId, idempotencyKey, payloadHash);
            if (existente != null) {
                return existente;
            }
        }

        CrdbRetryExecutor retry = crdbRetryExecutor.getIfAvailable();
        String claveParaPersistencia = repo != null ? idempotencyKey : null;
        Orden orden;
        try {
            orden = retry == null
                    ? ordenPersistenceService.crearOrdenDesdeCarrito(usuarioId, direccionId, metodopagoId,
                            claveParaPersistencia, payloadHash)
                    : retry.execute(() -> ordenPersistenceService.crearOrdenDesdeCarrito(
                            usuarioId, direccionId, metodopagoId, claveParaPersistencia, payloadHash));
        } catch (ClaveIdempotenciaEnConflictoException conflicto) {
            Orden ordenGanadora = repo.buscarPorUsuarioYClave(usuarioId, idempotencyKey)
                    .map(s -> obtenerOrdenPorId(s.ordenId()))
                    .orElse(null);
            if (ordenGanadora == null) {
                throw conflicto;
            }
            return ordenGanadora;
        }

        try {
            facturaClient.generarFactura(orden.getOrdenId());
        } catch (Exception e) {
            // La orden YA está confirmada en la BD (commit hecho). Decide con el equipo:
            // reintento, marcar "pendiente_facturacion", o solo log + seguir.
            throw new IllegalStateException("Orden " + orden.getOrdenId() + " creada pero falló la facturación", e);
        }

        return orden;
    }

    private IdempotenciaRepository resolverRepositorioIdempotencia(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        IdempotenciaRepository repo = idempotenciaRepository.orElse(null);
        if (repo == null) {
            log.warn("Se recibio Idempotency-Key pero pedidos.idempotencia.enabled=false; se ignora la cabecera");
        }
        return repo;
    }

    private Orden buscarOrdenPorClaveExistente(IdempotenciaRepository repo, Integer usuarioId,
                                                String idempotencyKey, String payloadHash) {
        Optional<SolicitudIdempotente> existente = repo.buscarPorUsuarioYClave(usuarioId, idempotencyKey);
        if (existente.isEmpty()) {
            return null;
        }
        if (!existente.get().payloadHash().equals(payloadHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La clave de idempotencia " + idempotencyKey + " ya se uso con una petición distinta");
        }
        return obtenerOrdenPorId(existente.get().ordenId());
    }

    private String calcularPayloadHash(Integer direccionId, Integer metodopagoId) {
        String canonico = direccionId + "|" + metodopagoId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
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
