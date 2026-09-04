package com.tiendatech.inventario.infrastructure.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiendatech.inventario.application.reservation.StockReservationService;
import com.tiendatech.inventario.domain.reservation.ReservationCommand;
import com.tiendatech.inventario.domain.reservation.ReservationResult;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TcpReservationServer implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(TcpReservationServer.class);
    private final int port;
    private final int readTimeoutMs;
    private final ObjectMapper mapper;
    private final StockReservationService service;
    private final Tracer tracer;
    private final Propagator propagator;
    private final String serviceName;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public TcpReservationServer(@Value("${reservation.tcp.port:9091}") int port,
                                @Value("${reservation.tcp.read-timeout-ms:5000}") int readTimeoutMs,
                                @Value("${spring.application.name}") String serviceName,
                                ObjectMapper mapper, StockReservationService service,
                                Tracer tracer, Propagator propagator) {
        this.port = port;
        this.readTimeoutMs = readTimeoutMs;
        this.serviceName = serviceName;
        this.mapper = mapper;
        this.service = service;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override public void start() {
        if (running) return;
        running = true;
        clients.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        try (ServerSocket listener = new ServerSocket(port)) {
            serverSocket = listener;
            LOG.info("Canal TCP de reservas escuchando en {} (framing int32 big-endian)", port);
            while (running) {
                Socket accepted = listener.accept();
                clients.submit(() -> handle(accepted));
            }
        } catch (Exception error) {
            if (running) LOG.error("Terminó el servidor TCP de reservas", error);
        } finally {
            running = false;
        }
    }

    private void handle(Socket socket) {
        try (socket;
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(readTimeoutMs);
            byte[] payload;
            while ((payload = LengthPrefixedJson.read(input)) != null) {
                LengthPrefixedJson.write(output, mapper.writeValueAsBytes(handleOne(payload)));
            }
        } catch (Exception error) {
            LOG.debug("Conexión TCP cerrada: {}", error.getMessage());
        }
    }

    /**
     * Deserializa el sobre de transporte (comando + traceparent + businessTraceId
     * opcionales), extrae el contexto de traza propagado manualmente por
     * LengthPrefixedTcpReservationClient (pedidos-service) y procesa la
     * reserva dentro de un span SERVER "reservation.tcp.reconcile", hijo de
     * ese contexto -- asi la reserva de stock queda en la misma traza que el
     * resto de la compra en Jaeger. Ademas publica el businessTraceId (el
     * X-Trace-Id de negocio del request HTTP original en pedidos-service) en
     * el MDC con las mismas claves "service"/"trace_id" que usa
     * HttpObservabilityFilter en toda la aplicacion, para que el log de este
     * canal TCP -- que nunca pasa por ese filtro, al no ser HTTP -- quede
     * correlacionado en Kibana/logs JSON con el resto de la operacion por el
     * mismo identificador manual, no solo por el trace ID de OTel.
     */
    private ReservationResult handleOne(byte[] payload) {
        WireEnvelope envelope;
        ReservationCommand command;
        try {
            envelope = mapper.readValue(payload, WireEnvelope.class);
            command = envelope.command();
        } catch (Exception malformed) {
            return new ReservationResult(false, malformed.getMessage(), 0, 0, 0, "", false);
        }
        Map<String, String> carrier = envelope.traceparent() == null
                ? Collections.emptyMap() : Map.of("traceparent", envelope.traceparent());
        Span span = propagator.extract(carrier, Map::get)
                .name("reservation.tcp.reconcile").kind(Span.Kind.SERVER).start();
        span.tag("reservation.cartId", String.valueOf(command.cartId()));
        span.tag("reservation.productId", String.valueOf(command.productId()));
        String businessTraceId = envelope.businessTraceId();
        MDC.put("service", serviceName);
        MDC.put("trace_id", businessTraceId == null || businessTraceId.isBlank() ? "untracked" : businessTraceId);
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            ReservationResult result = service.reconcile(command);
            if (!result.accepted()) span.tag("reservation.rejected", result.message());
            LOG.info("reservation_tcp_completed accepted={} cartId={} productId={}",
                    result.accepted(), command.cartId(), command.productId());
            return result;
        } catch (Exception error) {
            span.error(error);
            LOG.warn("reservation_tcp_failed cartId={} productId={}", command.cartId(), command.productId(), error);
            return new ReservationResult(false, error.getMessage(), 0, 0, 0, "", false);
        } finally {
            span.end();
            MDC.remove("service");
            MDC.remove("trace_id");
        }
    }

    @Override public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        clients.shutdownNow();
    }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MIN_VALUE + 100; }

    /** Sobre de transporte simetrico al de LengthPrefixedTcpReservationClient en pedidos-service. */
    private record WireEnvelope(ReservationCommand command, String traceparent, String businessTraceId) {}
}
