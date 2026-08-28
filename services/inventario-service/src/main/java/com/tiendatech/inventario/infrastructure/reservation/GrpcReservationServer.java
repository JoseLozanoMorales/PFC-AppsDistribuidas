package com.tiendatech.inventario.infrastructure.reservation;

import com.tiendatech.inventario.application.reservation.StockReservationService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class GrpcReservationServer implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcReservationServer.class);
    private final int port;
    private final StockReservationService service;
    private Server server;
    private volatile boolean running;

    public GrpcReservationServer(@Value("${reservation.grpc.port:9092}") int port,
                                 StockReservationService service) {
        this.port = port;
        this.service = service;
    }

    @Override public void start() {
        try {
            server = ServerBuilder.forPort(port).addService(new GrpcReservationEndpoint(service)).build().start();
            running = true;
            LOG.info("Servidor gRPC de reservas escuchando en {}", port);
        } catch (Exception error) { throw new IllegalStateException("No se pudo iniciar gRPC", error); }
    }
    @Override public void stop() { if (server != null) server.shutdownNow(); running = false; }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MIN_VALUE + 101; }
}
