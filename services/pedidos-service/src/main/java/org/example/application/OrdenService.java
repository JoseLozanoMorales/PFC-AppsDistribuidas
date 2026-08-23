package org.example.application;

import org.example.domain.ClaveIdempotenciaEnConflictoException;
import org.example.domain.DetalleOrden;
import org.example.domain.IdempotenciaRepository;
import org.example.domain.Orden;
import org.example.domain.OrdenRepository;
import org.example.domain.PageResponse;
import org.example.domain.Paginacion;
import org.example.domain.SolicitudIdempotente;
import org.example.domain.FacturaPort;
import org.example.domain.CrdbRetryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    private final OrdenRepository ordenRepository;
    private final FacturaPort facturaClient;
    private final ObjectProvider<CrdbRetryPort> crdbRetryExecutor;
    private final Optional<IdempotenciaRepository> idempotenciaRepository;

    @Autowired
    public OrdenService(OrdenRepository ordenRepository, FacturaPort facturaClient,
                        ObjectProvider<CrdbRetryPort> crdbRetryExecutor,
                        Optional<IdempotenciaRepository> idempotenciaRepository) {
        this.ordenRepository = ordenRepository;
        this.facturaClient = facturaClient;
        this.crdbRetryExecutor = crdbRetryExecutor;
        this.idempotenciaRepository = idempotenciaRepository;
    }

    public PageResponse<Orden> listarOrdenes(Paginacion paginacion) {
        return ordenRepository.listarOrdenes(paginacion);
    }

    public Orden obtenerOrdenPorId(Integer ordenId) {
        return ordenRepository.obtenerPorId(ordenId);
    }

    public PageResponse<Orden> listarOrdenesPorUsuario(Integer usuarioId, Paginacion paginacion) {
        return ordenRepository.listarPorUsuario(usuarioId, paginacion);
    }

    public PageResponse<DetalleOrden> obtenerDetalleOrden(Integer ordenId, LocalDate fecha, Paginacion paginacion) {
        return ordenRepository.obtenerDetalle(ordenId, fecha, paginacion);
    }

    // Orquesta: 1) crea la orden en una transacción que hace COMMIT antes de seguir,
    // 2) recién después llama a ventas-service (ya puede ver la orden en la BD).
    //
    // idempotencyKey es opcional (header Idempotency-Key de POST /checkout, ver
    // presentation.OrdenController). Si viene y el repositorio esta activo (ver
    // docs/idempotencia.sql), se resuelve en 3 pasos:
    //   1) pre-chequeo (fuera de transaccion, solo optimizacion): si ya existe una
    //      solicitud con esa clave para el usuario, devuelve esa orden sin repetir
    //      todo el flujo ni volver a llamar a facturacion. Hash distinto -> 409.
    //   2) si no existe, ejecuta el flujo normal; ordenRepository.crear registra
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

        CrdbRetryPort retry = crdbRetryExecutor.getIfAvailable();
        String claveParaPersistencia = repo != null ? idempotencyKey : null;
        Orden orden;
        try {
            orden = retry == null
                    ? ordenRepository.crear(usuarioId, direccionId, metodopagoId, claveParaPersistencia, payloadHash)
                    : retry.execute(() -> ordenRepository.crear(
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
}
