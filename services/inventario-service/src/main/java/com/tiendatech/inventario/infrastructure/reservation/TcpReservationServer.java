package com.tiendatech.inventario.infrastructure.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiendatech.inventario.application.reservation.StockReservationService;
import com.tiendatech.inventario.domain.reservation.ReservationCommand;
import com.tiendatech.inventario.domain.reservation.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TcpReservationServer implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(TcpReservationServer.class);
    private final int port;
    private final int readTimeoutMs;
    private final ObjectMapper mapper;
    private final StockReservationService service;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public TcpReservationServer(@Value("${reservation.tcp.port:9091}") int port,
                                @Value("${reservation.tcp.read-timeout-ms:5000}") int readTimeoutMs,
                                ObjectMapper mapper, StockReservationService service) {
        this.port = port;
        this.readTimeoutMs = readTimeoutMs;
        this.mapper = mapper;
        this.service = service;
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
                ReservationResult result;
                try {
                    ReservationCommand command = mapper.readValue(payload, ReservationCommand.class);
                    result = service.reconcile(command);
                } catch (Exception error) {
                    result = new ReservationResult(false, error.getMessage(), 0, 0, 0, "", false);
                }
                LengthPrefixedJson.write(output, mapper.writeValueAsBytes(result));
            }
        } catch (Exception error) {
            LOG.debug("Conexión TCP cerrada: {}", error.getMessage());
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
}
